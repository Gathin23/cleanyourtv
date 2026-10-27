"""Tela de fundo que fica atrás do jogo na TV: quando o jogo fecha, aparece ela em vez da área de trabalho
do Windows, até a TV voltar para a home. Aberta e fechada pelo agent.py."""
import tkinter as tk

BG = "#0a0e24"

root = tk.Tk()
root.title("Central de Jogos")
root.configure(bg=BG, cursor="none")
root.attributes("-fullscreen", True)
root.overrideredirect(True)

w, h = root.winfo_screenwidth(), root.winfo_screenheight()
c = tk.Canvas(root, width=w, height=h, bg=BG, highlightthickness=0)
c.pack(fill="both", expand=True)
# Fundo em degradê azul, como a TV.
steps = 60
for i in range(steps):
    t = i / steps
    r, g, b = int(10 + 18 * t), int(14 + 16 * t), int(36 + 60 * t)
    c.create_rectangle(0, h * i / steps, w, h * (i + 1) / steps + 1, fill="#%02x%02x%02x" % (r, g, b), width=0)
c.create_text(w // 2, h // 2 - 20, text="Voltando para a TV…", fill="white", font=("Segoe UI Semibold", 34))
c.create_text(w // 2, h // 2 + 34, text="Preparando o ambiente", fill="#aab0d0", font=("Segoe UI", 18))

# Pontinhos animados.
dots = [c.create_oval(0, 0, 0, 0, fill="white", width=0) for _ in range(3)]
frame = [0]


def tick():
    frame[0] += 1
    for i, d in enumerate(dots):
        on = (frame[0] // 4) % 3 == i
        x, y, r = w // 2 - 30 + i * 30, h // 2 + 90, 7 if on else 5
        c.coords(d, x - r, y - r, x + r, y + r)
        c.itemconfig(d, fill="white" if on else "#5a6090")
    root.after(120, tick)


tick()
root.mainloop()
