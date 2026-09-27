import csv
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter, LogLocator, NullFormatter

ROOT = Path(__file__).resolve().parent.parent
TABLES = ROOT / "results" / "tables"
PLOTS = ROOT / "results" / "plots"

SURFACE = "#fcfcfb"
INK = "#0b0b0b"
INK_2 = "#52514e"
GRID = "#e4e3df"
THEORY = "#9a9994"
BLUE = "#2a78d6"
ORANGE = "#eb6834"
AQUA = "#1baf7a"
BLUE_LIGHT = "#86b6ef"
BLUE_DARK = "#184f95"

plt.rcParams.update({
    "figure.facecolor": SURFACE,
    "axes.facecolor": SURFACE,
    "savefig.facecolor": SURFACE,
    "axes.edgecolor": GRID,
    "axes.labelcolor": INK_2,
    "axes.titlecolor": INK,
    "axes.titlesize": 12,
    "axes.titleweight": "semibold",
    "axes.labelsize": 10,
    "xtick.color": INK_2,
    "ytick.color": INK_2,
    "xtick.labelsize": 9,
    "ytick.labelsize": 9,
    "axes.grid": True,
    "grid.color": GRID,
    "grid.linewidth": 0.8,
    "grid.linestyle": "-",
    "axes.spines.top": False,
    "axes.spines.right": False,
    "legend.frameon": False,
    "legend.fontsize": 9,
    "legend.labelcolor": INK_2,
    "legend.handlelength": 3.4,
    "font.size": 10,
    "lines.linewidth": 2,
    "lines.solid_capstyle": "round",
    "lines.solid_joinstyle": "round",
})

SIZES = [100, 1_000, 10_000, 100_000]

def read(name):
    with open(TABLES / name, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))

def pick(rows, key, **match):
    sel = [r for r in rows if all(r[k] == v for k, v in match.items())]
    sel.sort(key=lambda r: int(r["n"]))
    return [float(r[key]) for r in sel]

def n_axis(ax):
    ax.set_xscale("log")
    ax.set_xticks(SIZES)
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{int(v):,}"))
    ax.xaxis.set_minor_formatter(NullFormatter())
    ax.set_xlabel("n (elements initially stored)")

def log_y(ax, fmt=None):
    ax.set_yscale("log")
    ax.yaxis.set_major_locator(LogLocator(base=10))
    ax.yaxis.set_minor_formatter(NullFormatter())
    ax.yaxis.set_major_formatter(FuncFormatter(fmt or (lambda v, _: f"{v:g}")))
    ax.grid(True, which="major")
    ax.grid(False, which="minor")

def series(ax, y, color, label, marker="o", ls="-", x=SIZES):
    ax.plot(x, y, color=color, ls=ls, marker=marker, markersize=6.5,
            markeredgecolor=SURFACE, markeredgewidth=1.5, label=label, zorder=3)

def end_label(ax, x, y, text, dy=0, color=INK_2):
    ax.annotate(text, (x, y), xytext=(6, dy), textcoords="offset points",
                va="center", ha="left", fontsize=8.5, color=color)

def theory_line(ax, x, y, label):
    ax.plot(x, y, color=THEORY, lw=1.2, ls=(0, (1, 2)), label=label, zorder=2)

def save(fig, name):
    PLOTS.mkdir(parents=True, exist_ok=True)
    fig.savefig(PLOTS / name, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print("wrote", PLOTS / name)

def plot1_time_vs_n(w1, w2, w3, w4):
    fig, axs = plt.subplots(2, 2, figsize=(12, 9))
    fig.suptitle("Plot 1 — Execution time vs n (mean of 5 runs, log–log)",
                 fontsize=14, fontweight="semibold", color=INK, x=0.01, ha="left")

    ax = axs[0][0]
    series(ax, pick(w1, "avg_ms", structure="DynamicArray"), BLUE, "DynamicArray")
    series(ax, pick(w1, "avg_ms", structure="LinkedList"), ORANGE, "LinkedList", marker="s")
    ax.set_title("W1 · 10,000 × get(i)", loc="left")
    ax.set_ylabel("total time (ms)")
    n_axis(ax)
    log_y(ax)
    ax.legend(loc="upper left")

    ax = axs[0][1]
    series(ax, pick(w2, "avg_ms", structure="DynamicArray"), BLUE, "DynamicArray")
    series(ax, pick(w2, "avg_ms", structure="LinkedList"), ORANGE, "LinkedList", marker="s")
    ax.set_title("W2 · 1,000 × contains(x)", loc="left")
    ax.set_ylabel("total time (ms)")
    n_axis(ax)
    log_y(ax)
    ax.legend(loc="upper left")

    ax = axs[1][0]
    for s, color, mk in (("DynamicArray", BLUE, "o"), ("LinkedList", ORANGE, "s")):
        series(ax, pick(w3, "avg_ms", structure=s, position="front", operation="insert"),
               color, f"{s} · insert at 0", marker=mk)
        series(ax, pick(w3, "avg_ms", structure=s, position="middle", operation="insert"),
               color, f"{s} · insert at n/2", marker=mk, ls=(0, (4, 2)))
    ax.set_title("W3 · 1,000 insertions (removals: same shape, see tables)", loc="left")
    ax.set_ylabel("total time (ms)")
    n_axis(ax)
    log_y(ax)
    ax.legend(loc="upper left")

    ax = axs[1][1]
    series(ax, pick(w4, "avg_ms", phase="insert"), AQUA, "n × insert")
    series(ax, pick(w4, "avg_ms", phase="extractMin"), BLUE_DARK, "n × extractMin", marker="D")
    ax.set_title("W4 · Min-Heap, n inserts then n extractions", loc="left")
    ax.set_ylabel("total time (ms)")
    n_axis(ax)
    log_y(ax)
    ax.legend(loc="upper left")

    fig.tight_layout(rect=(0, 0, 1, 0.96))
    save(fig, "plot1_time_vs_n.png")

def plot2_operations_vs_n(w1, w2, w3, w4):
    fig, axs = plt.subplots(2, 2, figsize=(12, 9))
    fig.suptitle("Plot 2 — Counted operations vs n (theory dotted; hidden where it coincides with the measurement)",
                 fontsize=14, fontweight="semibold", color=INK, x=0.01, ha="left")
    xs = [100 * 10 ** (k / 20) for k in range(0, 61)]

    ax = axs[0][0]
    series(ax, pick(w1, "accesses", structure="DynamicArray"), BLUE, "DynamicArray: element accesses")
    series(ax, pick(w1, "accesses", structure="LinkedList"), ORANGE, "LinkedList: nodes visited", marker="s")
    theory_line(ax, xs, [10_000 * (x / 4 + 1) for x in xs], "theory: m·(n/4+1) list, m array")
    theory_line(ax, xs, [10_000 for _ in xs], None)
    ax.set_title("W1 · accesses for 10,000 get(i)", loc="left")
    ax.set_ylabel("count")
    n_axis(ax)
    log_y(ax, lambda v, _: f"{v:,.0f}")
    ax.legend(loc="center right", bbox_to_anchor=(1.0, 0.42))

    ax = axs[0][1]
    series(ax, pick(w2, "comparisons", structure="DynamicArray"), BLUE, "DynamicArray")
    series(ax, pick(w2, "comparisons", structure="LinkedList"), ORANGE, "LinkedList", marker="s", ls=(0, (4, 2)))
    theory_line(ax, xs, [500 * (x + 1) / 2 + 500 * x for x in xs], "theory: 500·(n+1)/2 + 500·n")
    ax.set_title("W2 · comparisons for 1,000 contains(x) — identical for both", loc="left")
    ax.set_ylabel("element comparisons")
    n_axis(ax)
    log_y(ax, lambda v, _: f"{v:,.0f}")
    ax.legend(loc="upper left")

    ax = axs[1][0]
    series(ax, pick(w3, "movements", structure="DynamicArray", position="front", operation="insert"),
           BLUE, "DynamicArray · shifts, insert at 0")
    series(ax, pick(w3, "movements", structure="DynamicArray", position="middle", operation="insert"),
           BLUE, "DynamicArray · shifts, insert at n/2", ls=(0, (4, 2)))
    series(ax, pick(w3, "node_visits", structure="LinkedList", position="middle", operation="insert"),
           ORANGE, "LinkedList · nodes visited, insert at n/2", marker="s", ls=(0, (4, 2)))
    series(ax, pick(w3, "node_visits", structure="LinkedList", position="front", operation="insert"),
           ORANGE, "LinkedList · nodes visited, insert at 0", marker="s")
    theory_line(ax, xs, [1000 * x + 499_500 for x in xs], "theory (formulas in the W3 table)")
    theory_line(ax, xs, [1000 * (x / 2 + 1) for x in xs], None)
    ax.set_title("W3 · work for 1,000 insertions", loc="left")
    ax.set_ylabel("count")
    n_axis(ax)
    log_y(ax, lambda v, _: f"{v:,.0f}")
    ax.legend(loc="center right", bbox_to_anchor=(1.0, 0.36), fontsize=8.5)

    ax = axs[1][1]
    ins = pick(w4, "comparisons_per_op", phase="insert")
    ext = pick(w4, "comparisons_per_op", phase="extractMin")
    series(ax, ins, AQUA, "insert: comparisons / op")
    series(ax, ext, BLUE_DARK, "extractMin: comparisons / op", marker="D")
    theory_line(ax, xs, [2 * math.log2(x) for x in xs], "bounds: 2·log₂n (extract), log₂n (insert)")
    theory_line(ax, xs, [math.log2(x) for x in xs], None)
    end_label(ax, xs[-1], 2 * math.log2(xs[-1]), "2·log₂n", color=THEORY)
    end_label(ax, xs[-1], math.log2(xs[-1]), "log₂n", color=THEORY)
    for x, y in zip(SIZES, ins):
        ax.annotate(f"{y:.2f}", (x, y), xytext=(0, 9), textcoords="offset points",
                    ha="center", fontsize=8.5, color=INK_2)
    for x, y in zip(SIZES, ext):
        ax.annotate(f"{y:.1f}", (x, y), xytext=(0, 9), textcoords="offset points",
                    ha="center", fontsize=8.5, color=INK_2)
    ax.set_title("W4 · comparisons per heap operation", loc="left")
    ax.set_ylabel("comparisons per operation")
    ax.set_ylim(0, 36)
    n_axis(ax)
    ax.set_xlim(70, 250_000)
    ax.legend(loc="upper left")

    fig.tight_layout(rect=(0, 0, 1, 0.96))
    save(fig, "plot2_operations_vs_n.png")

def plot3_shift_ablation(w3, w3_32m, w3_par, jdk, ll):
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5.2), gridspec_kw={"width_ratios": [1.1, 1]})
    fig.suptitle("Plot 3 — W3 ablation: the same shift algorithm and the same Θ(n) shift count, very different cost per shift",
                 fontsize=13, fontweight="semibold", color=INK, x=0.01, ha="left")

    def per_shift(rows):
        return pick(rows, "ns_per_unit_of_work", structure="DynamicArray", position="front", operation="insert")

    shifts = pick(w3, "predicted_work", structure="DynamicArray", position="front", operation="insert")
    jdk_ms = [float(r["avg_ms"]) for r in sorted(
        (r for r in jdk if r["structure"] == "java.util.ArrayList" and r["operation"].startswith("insert @ front")),
        key=lambda r: int(r["n"]))]
    arraycopy = [ms * 1e6 / s for ms, s in zip(jdk_ms, shifts)]

    lines = [
        (per_shift(w3), BLUE_DARK, "o", "DynamicArray loop · G1, 1 MB regions (default)"),
        (per_shift(w3_32m), BLUE, "s", "DynamicArray loop · G1, 32 MB regions"),
        (per_shift(w3_par), BLUE_LIGHT, "^", "DynamicArray loop · ParallelGC"),
        (arraycopy, AQUA, "D", "java.util.ArrayList · System.arraycopy (G1)"),
    ]
    for y, c, mk, lab in lines:
        series(ax1, y, c, lab, marker=mk)
        end_label(ax1, SIZES[-1], y[-1], f"{y[-1]:.2f} ns")
    ax1.set_title("ns per element shifted · 1,000 insertions at index 0", loc="left")
    ax1.set_ylabel("ns per shift (log scale)")
    n_axis(ax1)
    log_y(ax1, lambda v, _: f"{v:g}")
    ax1.set_xlim(70, 400_000)
    ax1.set_ylim(0.05, 60)
    ax1.legend(loc="upper left", fontsize=8.5)

    def at100k(rows, **m):
        return [float(r["avg_ms"]) for r in rows if r["n"] == "100000"
                and all(r[k] == v for k, v in m.items())][0]

    bars = [
        ("DynamicArray · G1 default", at100k(w3, structure="DynamicArray", position="middle", operation="insert"), BLUE_DARK),
        ("DynamicArray · G1 32 MB regions", at100k(w3_32m, structure="DynamicArray", position="middle", operation="insert"), BLUE),
        ("DynamicArray · ParallelGC", at100k(w3_par, structure="DynamicArray", position="middle", operation="insert"), BLUE_LIGHT),
        ("java.util.ArrayList (arraycopy)", at100k(jdk, structure="java.util.ArrayList", operation="insert @ middle x1000"), AQUA),
        ("LinkedList · 50 M node visits", at100k(ll, structure="LinkedList", position="middle", operation="insert"), ORANGE),
    ]
    labels = [b[0] for b in bars][::-1]
    vals = [b[1] for b in bars][::-1]
    cols = [b[2] for b in bars][::-1]
    ax2.barh(labels, vals, color=cols, height=0.55, zorder=3)
    for i, v in enumerate(vals):
        ax2.annotate(f"{v:,.1f} ms", (v, i), xytext=(5, 0), textcoords="offset points",
                     va="center", fontsize=9, color=INK)
    ax2.set_title("n = 100,000 · 1,000 insertions at n/2 (total time)", loc="left")
    ax2.set_xlabel("total time (ms)")
    ax2.set_xlim(0, max(vals) * 1.22)
    ax2.grid(axis="y", visible=False)
    ax2.tick_params(axis="y", length=0, labelsize=9)

    fig.tight_layout(rect=(0, 0, 1, 0.93))
    save(fig, "plot3_w3_shift_cost_ablation.png")

def plot4_cost_per_step(w1, w2, w4):
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5))
    fig.suptitle("Plot 4 — Time per elementary step: where the constant factors hide",
                 fontsize=13, fontweight="semibold", color=INK, x=0.01, ha="left")

    a_get = pick(w1, "ns_per_access", structure="DynamicArray")
    l_get = pick(w1, "ns_per_access", structure="LinkedList")
    a_cmp = pick(w2, "ns_per_comparison", structure="DynamicArray")
    l_cmp = pick(w2, "ns_per_comparison", structure="LinkedList")
    series(ax1, a_get, BLUE, "DynamicArray · per get(i) (1 access)")
    series(ax1, l_get, ORANGE, "LinkedList · per node visited in get(i)", marker="s")
    series(ax1, a_cmp, BLUE, "DynamicArray · per comparison in contains", ls=(0, (4, 2)))
    series(ax1, l_cmp, ORANGE, "LinkedList · per comparison in contains", marker="s", ls=(0, (4, 2)))
    ax1.set_title("W1 / W2 · ns per access or comparison", loc="left")
    ax1.set_ylabel("ns per step")
    ax1.set_ylim(0, max(a_get + l_get + a_cmp + l_cmp) * 1.15)
    n_axis(ax1)
    ax1.legend(loc="upper left", fontsize=8.5)

    ins = pick(w4, "ns_per_op", phase="insert")
    ext = pick(w4, "ns_per_op", phase="extractMin")
    ext_norm = [e / math.log2(n) for e, n in zip(ext, SIZES)]
    series(ax2, ins, AQUA, "insert · ns / op")
    series(ax2, ext_norm, BLUE_DARK, "extractMin · ns / (op · log₂n)", marker="D")
    for x, y in zip(SIZES, ext_norm):
        ax2.annotate(f"{y:.1f}", (x, y), xytext=(0, 9), textcoords="offset points",
                     ha="center", fontsize=8.5, color=INK_2)
    ax2.set_title("W4 · heap cost per op (extract normalised by log₂n)", loc="left")
    ax2.set_ylabel("ns")
    ax2.set_ylim(0, max(ins + ext_norm) * 1.2)
    n_axis(ax2)
    ax2.legend(loc="upper right", fontsize=8.5)

    fig.tight_layout(rect=(0, 0, 1, 0.93))
    save(fig, "plot4_cost_per_step.png")

def main():
    w1 = read("w1_random_access.csv")
    w2 = read("w2_search.csv")
    w3 = read("w3_insert_remove.csv")
    w4 = read("w4_heap.csv")
    jdk = read("jdk_reference.csv")
    plot1_time_vs_n(w1, w2, w3, w4)
    plot2_operations_vs_n(w1, w2, w3, w4)
    w3_32m = TABLES / "w3_insert_remove_g1_32mb_regions.csv"
    w3_par = TABLES / "w3_insert_remove_parallelgc.csv"
    if w3_32m.exists() and w3_par.exists():
        plot3_shift_ablation(w3, read(w3_32m.name), read(w3_par.name), jdk, w3)
    else:
        print("skipping plot 3: run the W3 ablation runs in run_benchmark.sh first")
    plot4_cost_per_step(w1, w2, w4)

if __name__ == "__main__":
    main()
