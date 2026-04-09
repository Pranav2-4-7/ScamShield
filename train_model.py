#!/usr/bin/env python3
"""
ScamShield TFLite Model Training Script
========================================
Adapts the CyberSorceress/Scam-Call-Detection model for on-device TFLite deployment.

Requirements:
    pip install tensorflow scikit-learn pandas numpy

Usage:
    python train_model.py
    Outputs: scam_detection.tflite, vocab.txt
"""

import json
import os
import re
import numpy as np
import tensorflow as tf
from tensorflow import keras
from sklearn.model_selection import train_test_split
from collections import Counter

# ── Config ─────────────────────────────────────────────────────────────────
MAX_VOCAB_SIZE  = 5000
MAX_SEQ_LENGTH  = 128
EMBEDDING_DIM   = 64
LSTM_UNITS      = 64
EPOCHS          = 15
BATCH_SIZE      = 32
OUTPUT_MODEL    = "scam_detection.tflite"
OUTPUT_VOCAB    = "vocab.txt"

# ── Extended scam training data ────────────────────────────────────────────
# Format: (text, label)  label=1 => scam, label=0 => legit
TRAINING_DATA = [
    # --- OTP Scams ---
    ("Please share the OTP sent to your registered mobile number to verify your account", 1),
    ("Your OTP is about to expire please tell me the 6 digit code now", 1),
    ("I am calling from SBI bank please give me your one time password immediately", 1),
    ("Do not share this OTP with anyone your account will be blocked", 1),
    ("The OTP you received is for KYC verification please confirm it now", 1),
    ("Enter the PIN number I am sending you to complete the transaction", 1),
    ("Share the verification code to process your refund", 1),

    # --- Bank Impersonation ---
    ("I am calling from HDFC bank your account has been frozen due to suspicious activity", 1),
    ("Your credit card has been blocked please verify your card details immediately", 1),
    ("This is an alert from ICICI bank unauthorized transaction detected", 1),
    ("Your KYC is incomplete account will be suspended within 24 hours", 1),
    ("I am the branch manager of SBI please share your account number for verification", 1),
    ("Your net banking access will be revoked unless you complete verification now", 1),
    ("We detected a fraud transaction of 50000 rupees please confirm your ATM pin", 1),
    ("Your Axis bank account shows suspicious login please verify your password", 1),

    # --- Government Impersonation ---
    ("I am calling from CBI your Aadhaar card is linked to money laundering case", 1),
    ("This is the cyber crime department you are under digital arrest do not move", 1),
    ("Income tax department has filed a case against you pay immediately to avoid arrest", 1),
    ("Your mobile number has been used for illegal activities TRAI will disconnect it", 1),
    ("Police complaint registered against you please call this number to resolve", 1),
    ("Narcotics department has seized a package in your name please cooperate", 1),
    ("Judge has issued arrest warrant against you pay fine now to cancel", 1),
    ("This is customs department a parcel with drugs was found in your name", 1),
    ("Your Aadhaar card is being misused you will be arrested in 2 hours", 1),

    # --- Urgency and Fear Tactics ---
    ("Act immediately or your account will be permanently closed today", 1),
    ("You have exactly 30 minutes to respond or legal action will be initiated", 1),
    ("This is your last and final warning do not ignore this call", 1),
    ("Do not tell anyone about this call keep it completely confidential", 1),
    ("Do not disconnect this call otherwise you will be arrested", 1),
    ("Transfer the money within one hour to avoid criminal charges", 1),
    ("Your property will be seized if you do not pay the penalty today", 1),

    # --- Prize Scams ---
    ("Congratulations you have won 25 lakh rupees in KBC lucky draw", 1),
    ("You are the lucky winner of our prize just pay processing fee to claim", 1),
    ("Your number was selected in government lottery please confirm details", 1),
    ("You have won an iPhone 15 in our annual lucky draw call now to claim", 1),

    # --- Tech Support Scams ---
    ("Your computer has been hacked I am calling from Microsoft technical support", 1),
    ("Please download AnyDesk so I can fix the virus on your device remotely", 1),
    ("Your phone has malware install this app immediately to remove it", 1),
    ("Share your screen using TeamViewer so we can secure your account", 1),
    ("Apple security team your iCloud account has been compromised give us access", 1),

    # --- Refund and KYC ---
    ("You are eligible for a refund of 8000 rupees please share bank details", 1),
    ("Excess amount was charged to your account we need your UPI PIN to refund", 1),
    ("Your KYC update is pending complete it now via video call", 1),
    ("Complete KYC immediately or your Paytm wallet will be deactivated today", 1),

    # --- Personal Info Requests ---
    ("Please confirm your Aadhaar number date of birth and full address", 1),
    ("Give me your PAN card number to verify your identity for this refund", 1),
    ("Share your ATM PIN CVV and expiry date to unlock your account", 1),
    ("To process this I need your mother's maiden name and account IFSC", 1),

    # ============ LEGITIMATE CALLS ============
    ("Hello this is a reminder for your doctor appointment tomorrow at 10 AM", 0),
    ("Hi I wanted to confirm if you received the package we sent last week", 0),
    ("This is to inform you that your flight has been rescheduled to 3 PM", 0),
    ("Hey are we still meeting for lunch today around noon", 0),
    ("I am calling to follow up on the job application you submitted", 0),
    ("Your order has been shipped and will arrive by Thursday", 0),
    ("Just wanted to check if you are free this weekend for the family gathering", 0),
    ("Hi this is Priya can you please call me back when you get a chance", 0),
    ("Calling to confirm your service appointment for tomorrow morning", 0),
    ("Hey did you get my message about the project deadline change", 0),
    ("This is customer support calling back regarding your complaint raised yesterday", 0),
    ("Your electricity bill is due please visit the nearest office to pay", 0),
    ("We are conducting a survey about our services would you have 2 minutes", 0),
    ("Hello calling from the school to inform about tomorrow being a holiday", 0),
    ("Hi mom just checking in wanted to know if you need anything from the market", 0),
    ("The pizza you ordered will arrive in about 20 minutes", 0),
    ("Reminder that your car service is due please book an appointment", 0),
    ("This is your landlord calling about the rent receipt for this month", 0),
    ("Hi I got your number from the advertisement is the flat still available", 0),
    ("Calling to schedule a plumber visit as requested please confirm timing", 0),
]

# ── Preprocessing ──────────────────────────────────────────────────────────

def clean_text(text):
    text = text.lower()
    text = re.sub(r'[^a-z0-9\s]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def build_vocab(texts, max_size):
    all_words = []
    for text in texts:
        all_words.extend(clean_text(text).split())
    counter = Counter(all_words)
    vocab = {'[PAD]': 0, '[UNK]': 1}
    for word, _ in counter.most_common(max_size - 2):
        vocab[word] = len(vocab)
    return vocab

def tokenize(text, vocab, max_len):
    words = clean_text(text).split()
    ids = [vocab.get(w, vocab['[UNK]']) for w in words[:max_len]]
    # Pad to max_len
    ids += [0] * (max_len - len(ids))
    return ids

# ── Model Architecture ─────────────────────────────────────────────────────

def build_model(vocab_size, max_len, embed_dim, lstm_units):
    inputs = keras.Input(shape=(max_len,), dtype=tf.int32)
    x = keras.layers.Embedding(vocab_size, embed_dim, mask_zero=True)(inputs)
    x = keras.layers.Bidirectional(keras.layers.LSTM(lstm_units, return_sequences=True))(x)
    x = keras.layers.GlobalMaxPooling1D()(x)
    x = keras.layers.Dense(64, activation='relu')(x)
    x = keras.layers.Dropout(0.3)(x)
    outputs = keras.layers.Dense(1, activation='sigmoid')(x)
    model = keras.Model(inputs, outputs)
    model.compile(
        optimizer='adam',
        loss='binary_crossentropy',
        metrics=['accuracy']
    )
    return model

# ── Main ───────────────────────────────────────────────────────────────────

def main():
    print("ScamShield Model Training")
    print("=" * 40)

    texts  = [d[0] for d in TRAINING_DATA]
    labels = np.array([d[1] for d in TRAINING_DATA])

    print(f"Total samples: {len(texts)} ({sum(labels)} scam, {len(labels)-sum(labels)} legit)")

    # Build vocab
    vocab = build_vocab(texts, MAX_VOCAB_SIZE)
    print(f"Vocabulary size: {len(vocab)}")

    # Tokenize
    X = np.array([tokenize(t, vocab, MAX_SEQ_LENGTH) for t in texts])
    y = labels

    # Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # Build and train
    model = build_model(len(vocab), MAX_SEQ_LENGTH, EMBEDDING_DIM, LSTM_UNITS)
    model.summary()

    history = model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        verbose=1
    )

    val_acc = max(history.history['val_accuracy'])
    print(f"\nBest validation accuracy: {val_acc:.4f}")

    # Convert to TFLite
    print("\nConverting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]  # Dynamic quantization
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    with open(OUTPUT_MODEL, 'wb') as f:
        f.write(tflite_model)
    print(f"TFLite model saved: {OUTPUT_MODEL} ({len(tflite_model) / 1024:.1f} KB)")

    # Save vocabulary
    with open(OUTPUT_VOCAB, 'w') as f:
        for word in vocab:
            f.write(word + '\n')
    print(f"Vocabulary saved: {OUTPUT_VOCAB}")

    # Quick inference test
    print("\nInference test:")
    test_cases = [
        ("Please share your OTP immediately your account will be blocked", 1),
        ("Hi calling to confirm your appointment tomorrow", 0),
        ("I am from CBI you are under digital arrest do not move", 1),
    ]

    interpreter = tf.lite.Interpreter(model_path=OUTPUT_MODEL)
    interpreter.allocate_tensors()
    input_details  = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    for text, expected in test_cases:
        tokens = np.array([tokenize(text, vocab, MAX_SEQ_LENGTH)], dtype=np.int32)
        interpreter.set_tensor(input_details[0]['index'], tokens)
        interpreter.invoke()
        score = interpreter.get_tensor(output_details[0]['index'])[0][0]
        result = "SCAM" if score > 0.5 else "SAFE"
        print(f"  [{result} {score:.3f}] {text[:60]}")

    print("\nDone! Copy scam_detection.tflite and vocab.txt to:")
    print("  app/src/main/assets/")


if __name__ == "__main__":
    main()
