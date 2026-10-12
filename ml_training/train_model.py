#!/usr/bin/env python3
import json
import os

import numpy as np
import tensorflow as tf

SEED = 14                       # variant number — for reproducibility
WINDOW = 20
             # ML_WINDOW in AppConstants
SAMPLES_PER_CLASS = 2000
LUX_MAX = 1000.0                # ML_LUX_MAX in AppConstants
LABELS = ["CLOSED", "FLICKER", "OPENED", "TAMPERING"]

# Output file names (MODEL_FILE and LABELS_FILE match SafeAccessClassifier.kt)
MODEL_FILE = "safe_access_model.tflite"
LABELS_FILE = "labels.txt"
METRICS_FILE = "metrics.json"
CONFUSION_MATRIX_FILE = "confusion_matrix.png"
CLASS_EXAMPLES_FILE = "class_examples.png"

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets")
rng = np.random.default_rng(SEED)


def normalize(lux):
    """The same preprocessing as in LightWindowBuffer.kt: ln(1+lux)/ln(1+1000), clipped to [0, 1]."""
    return np.clip(np.log1p(np.maximum(lux, 0.0)) / np.log1p(LUX_MAX), 0.0, 1.0)


def dark(n):
    """Darkness inside the closed safe: 0–8 lux + sensor noise."""
    return np.clip(rng.uniform(0, 8) + rng.normal(0, 0.4, n), 0, None)


def bright(n):
    """Light with the door open: 20–500 lux with a ±8 % fluctuation."""
    return rng.uniform(20, 500) * (1 + rng.normal(0, 0.08, n))


def gen_closed():
    return dark(WINDOW)


def gen_flicker():
    """A short flash (1–5 samples, i.e. ≤ 0.5 s) and a return to darkness."""
    w = dark(WINDOW)
    length = rng.integers(1, 6)
    start = rng.integers(0, WINDOW - length + 1)
    w[start:start + length] = bright(length)
    return w


def gen_opened():
    """A sharp jump and a steadily high value for at least 0.6 s."""
    w = dark(WINDOW)
    high = rng.integers(6, WINDOW + 1)          # length of the bright section
    if rng.random() < 0.8:                      # the safe was opened and is kept open
        w[WINDOW - high:] = bright(high)
    else:                                       # opened and closed within the window
        start = rng.integers(0, WINDOW - high + 1)
        w[start:start + high] = bright(high)
    return w


def gen_tampering():
    """Flickering: a torch through a gap or repeated attempts — at least two flashes."""
    while True:
        w = dark(WINDOW)
        i = rng.integers(0, 4)
        flashes = 0
        while i < WINDOW:
            length = rng.integers(1, 5)
            w[i:i + length] = rng.uniform(15, 300) * (1 + rng.normal(0, 0.1, len(w[i:i + length])))
            flashes += 1
            i += length + rng.integers(1, 5)    # dark pause between flashes
        if flashes >= 2:
            return w


GENERATORS = [gen_closed, gen_flicker, gen_opened, gen_tampering]


def make_dataset():
    x = np.stack([normalize(g()) for g in GENERATORS for _ in range(SAMPLES_PER_CLASS)])
    y = np.repeat(np.arange(len(LABELS)), SAMPLES_PER_CLASS)
    order = rng.permutation(len(x))
    return x[order].astype(np.float32), y[order]


def main():
    tf.keras.utils.set_random_seed(SEED)
    x, y = make_dataset()
    split = int(len(x) * 0.8)
    x_train, y_train, x_test, y_test = x[:split], y[:split], x[split:], y[split:]

    model = tf.keras.Sequential([
        tf.keras.Input(shape=(WINDOW,), name="light_window"),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dense(16, activation="relu"),
        tf.keras.layers.Dense(len(LABELS), activation="softmax"),
    ])
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    model.fit(x_train, y_train, epochs=40, batch_size=32, validation_split=0.1, verbose=2)

    _, accuracy = model.evaluate(x_test, y_test, verbose=0)
    predicted = model.predict(x_test, verbose=0).argmax(axis=1)
    matrix = tf.math.confusion_matrix(y_test, predicted, num_classes=len(LABELS)).numpy()
    print(f"\nТочність на тестовій вибірці ({len(x_test)} вікон): {accuracy:.4f}")
    print("Матриця помилок (рядки — справжній клас, стовпці — прогноз):")
    print(" " * 11 + "".join(f"{name:>11}" for name in LABELS))
    for name, row in zip(LABELS, matrix):
        print(f"{name:>11}" + "".join(f"{v:>11}" for v in row))

    # ---- Conversion to TensorFlow Lite ----
    tflite_model = tf.lite.TFLiteConverter.from_keras_model(model).convert()
    os.makedirs(ASSETS, exist_ok=True)
    model_path = os.path.join(ASSETS, MODEL_FILE)
    with open(model_path, "wb") as f:
        f.write(tflite_model)
    with open(os.path.join(ASSETS, LABELS_FILE), "w", encoding="utf-8") as f:
        f.write("\n".join(LABELS) + "\n")
    print(f"\nЗбережено {model_path} ({len(tflite_model)} байт)")

    # ---- Checking the .tflite with the same interpreter as on the phone ----
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    print(f"Вхідний тензор:  {inp['name']} shape={inp['shape'].tolist()} dtype={inp['dtype'].__name__}")
    print(f"Вихідний тензор: {out['name']} shape={out['shape'].tolist()} dtype={out['dtype'].__name__}")
    tflite_correct = 0
    for window, label in zip(x_test, y_test):
        interpreter.set_tensor(inp["index"], window[None, :])
        interpreter.invoke()
        tflite_correct += int(interpreter.get_tensor(out["index"])[0].argmax() == label)
    tflite_accuracy = tflite_correct / len(x_test)
    print(f"Точність .tflite на тестовій вибірці: {tflite_accuracy:.4f}")

    with open(os.path.join(HERE, METRICS_FILE), "w", encoding="utf-8") as f:
        json.dump({"accuracy": float(accuracy), "tflite_accuracy": tflite_accuracy,
                   "test_size": len(x_test), "labels": LABELS, "confusion_matrix": matrix.tolist(),
                   "model_bytes": len(tflite_model), "tensorflow": tf.__version__}, f, indent=2)
    save_plots(matrix, x_test, y_test)


def save_plots(matrix, x_test, y_test):
    """Figures for the report: the confusion matrix and example windows of each class."""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib не встановлено — рисунки пропущено")
        return
    fig, ax = plt.subplots(figsize=(5.2, 4.6), dpi=160)
    ax.imshow(matrix, cmap="Blues")
    ax.set_xticks(range(len(LABELS)), LABELS, rotation=30)
    ax.set_yticks(range(len(LABELS)), LABELS)
    ax.set_xlabel("Прогноз моделі")
    ax.set_ylabel("Справжній клас")
    for i in range(len(LABELS)):
        for j in range(len(LABELS)):
            ax.text(j, i, matrix[i, j], ha="center", va="center",
                    color="white" if matrix[i, j] > matrix.max() / 2 else "black")
    fig.tight_layout()
    fig.savefig(os.path.join(HERE, CONFUSION_MATRIX_FILE))

    fig, axes = plt.subplots(1, 4, figsize=(11, 2.6), dpi=160, sharey=True)
    for cls, ax in enumerate(axes):
        for window in x_test[y_test == cls][:5]:
            ax.plot(np.arange(WINDOW) * 0.1, window, lw=1.2)
        ax.set_title(LABELS[cls])
        ax.set_xlabel("час, с")
        ax.set_ylim(-0.05, 1.05)
    axes[0].set_ylabel("нормалізоване значення")
    fig.tight_layout()
    fig.savefig(os.path.join(HERE, CLASS_EXAMPLES_FILE))


if __name__ == "__main__":
    main()
