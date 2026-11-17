#!/usr/bin/env python3
"""Genera los layouts y drawables de los 10 diseños nuevos del contador (estilo Apple / One UI).

Uso (desde la raíz del repo):  python3 android/herramientas/generar_temas_nuevos.py android/app/src/main/res
Los widget_{vidrio,anillo,puntos,papel,tinta,transparente,barra,pastel,bloques,coral}*.xml y sus
marcos/botones salen de aquí: se cambian aquí y se vuelve a correr, no a mano. Los colores viven en
values/colors_temas.xml y los textos del pie en values/strings_temas.xml.
"""
import os, sys

RES = sys.argv[1]
A = 'xmlns:android="http://schemas.android.com/apk/res/android"'
COR = "♥&#xFE0E;"


# Tamaños de letra en escala áurea de dos bases (10 y 13, cada paso doble ×1,618).
# Cada textSize se lleva al valor más cercano (en proporción) al escribir el archivo.
ESCALA_LETRA = [8, 10, 13, 16, 21, 26, 34, 42, 55]


def a_escala(texto):
    import math, re
    def uno(m):
        v = float(m.group(1))
        return f'textSize="{min(ESCALA_LETRA, key=lambda e: abs(math.log(v / e)))}sp"'
    return re.sub(r'textSize="([0-9.]+)sp"', uno, texto)


def escribir(carpeta, nombre, texto):
    with open(os.path.join(RES, carpeta, nombre + ".xml"), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n' + a_escala(texto).strip() + "\n")


# ---------- piezas ----------
def tv(texto, tam, color, id=None, fuente="sans-serif", w="wrap_content", h="wrap_content", extra=""):
    i = f'android:id="@+id/{id}" ' if id else ""
    return (f'<TextView {i}android:layout_width="{w}" android:layout_height="{h}" android:maxLines="1" '
            f'android:includeFontPadding="false" android:text="{texto}" android:fontFamily="{fuente}" '
            f'android:textColor="@color/{color}" android:textSize="{tam}sp" {extra}/>')


def reloj(tam, color, fuente="sans-serif", extra=""):
    return (f'<Chronometer android:id="@+id/minseg" android:layout_width="wrap_content" android:layout_height="wrap_content" '
            f'android:maxLines="1" android:includeFontPadding="false" android:fontFamily="{fuente}" '
            f'android:textColor="@color/{color}" android:textSize="{tam}sp" {extra}/>')


def fila(hijos, w="wrap_content", h="wrap_content", extra='android:gravity="center_vertical"'):
    return (f'<LinearLayout android:layout_width="{w}" android:layout_height="{h}" android:orientation="horizontal" {extra}>'
            + "\n".join(hijos) + "</LinearLayout>")


def col(hijos, w="wrap_content", h="wrap_content", extra=""):
    return (f'<LinearLayout android:layout_width="{w}" android:layout_height="{h}" android:orientation="vertical" {extra}>'
            + "\n".join(hijos) + "</LinearLayout>")


# Márgenes en escala áurea (cada paso ≈ el anterior × 1,618): 5 · 8 · 13 · 21 dp.
# El completo lleva 21 y la franja y el mini 13, así que guardan entre sí la razón φ.
# Los diseños con cajas (Bloques, y a mano Cine, Cielo, Neón, Material y Azul) van un paso más abajo,
# porque las cajas ya hacen de margen: completo 13 afuera y 8 entre cajas; franja y mini 8 afuera y 5 entre cajas.
PADDING = {"completo": "21dp", "franja": "13dp", "compacto": "13dp"}
PADDING_BLOQUES = {"completo": "13dp", "franja": "8dp", "compacto": "8dp"}


def raiz(comentario, fondo, hijos, orient="vertical", pad=None, extra=""):
    tamano = next((k for k in PADDING if k in comentario), None)
    if tamano:
        pad = (PADDING_BLOQUES if "Bloques" in comentario else PADDING)[tamano]
    pad = pad or "13dp"
    bg = f'android:background="@drawable/{fondo}" ' if fondo else ""
    return (f"<!-- {comentario} -->\n"
            f'<LinearLayout {A} android:id="@+id/raiz" android:layout_width="match_parent" android:layout_height="match_parent" '
            f'{bg}android:orientation="{orient}" android:padding="{pad}" {extra}>' + "\n".join(hijos) + "</LinearLayout>")


def boton(fondo, color, tam=15, texto=COR, w="wrap_content", h="wrap_content", extra=""):
    """enviar_toque. En la franja solo puede llevar el ♥: el latido le cambia el texto."""
    return (f'<TextView android:id="@+id/enviar_toque" android:layout_width="{w}" android:layout_height="{h}" '
            f'android:background="@drawable/{fondo}" android:contentDescription="Mandar un toque" android:gravity="center" '
            f'android:maxLines="1" android:text="{texto}" android:textColor="@color/{color}" android:textSize="{tam}sp" '
            f'android:fontFamily="sans-serif-medium" android:includeFontPadding="false" {extra}/>')


def corazon_mini(color, tam=12, extra="", w="wrap_content", h="wrap_content"):
    """abrir_pagina: el ♥ del mini que late y abre la página."""
    return (f'<TextView android:id="@+id/abrir_pagina" android:layout_width="{w}" android:layout_height="{h}" '
            f'android:contentDescription="Abrir la página de ROMMICINES" android:includeFontPadding="false" '
            f'android:text="{COR}" android:textColor="@color/{color}" android:textSize="{tam}sp" {extra}/>')


def pie(texto, tam, color, fuente="sans-serif", w="0dp", extra='android:layout_weight="1"'):
    return (f'<TextView android:id="@+id/pie" android:layout_width="{w}" android:layout_height="wrap_content" '
            f'android:ellipsize="end" android:maxLines="1" android:text="{texto}" android:fontFamily="{fuente}" '
            f'android:textColor="@color/{color}" android:textSize="{tam}sp" {extra}/>')


def meta(tam, color, extra="", w="wrap_content"):
    return (f'<TextView android:id="@+id/meta" android:layout_width="{w}" android:layout_height="wrap_content" '
            f'android:ellipsize="end" android:maxLines="1" android:text="faltan 22 días para 2 meses" '
            f'android:textColor="@color/{color}" android:textSize="{tam}sp" {extra}/>')


def grafico(w, h, extra=""):
    return (f'<ImageView android:id="@+id/grafico" android:layout_width="{w}" android:layout_height="{h}" '
            f'android:importantForAccessibility="no" android:scaleType="fitCenter" {extra}/>')


def barra(alto="6dp", extra=""):
    return (f'<ProgressBar android:id="@+id/barra" style="@android:style/Widget.ProgressBar.Horizontal" '
            f'android:layout_width="match_parent" android:layout_height="{alto}" android:max="1000" android:progress="630" '
            f'android:progressDrawable="@drawable/barra_progreso" {extra}/>')


def hueco(w="0dp", h="0dp", peso=1):
    # Un FrameLayout vacío: un widget no admite View sueltas.
    return f'<FrameLayout android:layout_width="{w}" android:layout_height="{h}" android:layout_weight="{peso}" />'


def linea_sep(color):
    return f'<ImageView android:layout_width="match_parent" android:layout_height="1dp" android:importantForAccessibility="no" android:background="@color/{color}" />'


def dias(tam, color, fuente="sans-serif-medium", extra='android:letterSpacing="-0.03"'):
    return tv("38", tam, color, "dias", fuente, extra=extra)


def horas(tam, color, fuente="sans-serif"):
    return tv("04", tam, color, "horas", fuente)


def etiqueta(tam, color, fuente="sans-serif", margen="4dp"):
    return tv("días", tam, color, "dias_etiqueta", fuente, extra=f'android:layout_marginStart="{margen}"')


def hora_reloj(tam, color, fuente="sans-serif", sep=" h · "):
    """04 h · 37:48"""
    return fila([horas(tam, color, fuente), tv(sep, tam, color, fuente=fuente), reloj(tam, color, fuente)])


SOMBRA = 'android:shadowColor="@color/transparente_sombra" android:shadowRadius="6" android:shadowDy="1"'

# ================= ANILLO (3) =================
def anillo():
    escribir("layout", "widget_anillo", raiz("Contador completo, diseño Anillo: el anillo avanza hacia el próximo mes juntos.", "marco_grafito", [
        '<FrameLayout android:layout_width="96dp" android:layout_height="96dp">',
        grafico("match_parent", "match_parent"),
        boton("boton_grafito", "anillo_rosa", 25, w="44dp", h="44dp", extra='android:layout_gravity="center"'),
        "</FrameLayout>",
        col([
            pie("Juntos", 12, "anillo_rosa", "sans-serif-medium", w="match_parent", extra=""),
            fila([dias(30, "blanco", "sans-serif-medium"), etiqueta(30, "blanco", "sans-serif-medium", "6dp")],
                 extra='android:gravity="bottom" android:layout_marginTop="2dp"'),
            fila([horas(13, "anillo_gris"), tv(" h · ", 13, "anillo_gris"), reloj(13, "anillo_gris")], extra='android:layout_marginTop="3dp"'),
            meta(12, "anillo_rosa", 'android:layout_marginTop="3dp"'),
        ], extra='android:layout_marginStart="16dp"'),
    ], orient="horizontal", extra='android:gravity="center"'))

    escribir("layout", "widget_anillo_franja", raiz("Contador en franja (3×1, 4×1), diseño Anillo.", "marco_grafito", [
        grafico("44dp", "44dp"),
        col([
            fila([dias(22, "blanco"), etiqueta(22, "blanco", "sans-serif-medium")]),
            fila([horas(12, "anillo_gris"), tv(" h · ", 12, "anillo_gris"), reloj(12, "anillo_gris"),
                  tv(" · ", 12, "anillo_gris"), meta(12, "anillo_rosa", w="0dp", extra='android:layout_weight="1"')],
                 w="match_parent", extra='android:layout_marginTop="2dp"'),
        ], w="0dp", extra='android:layout_weight="1" android:layout_marginStart="12dp"'),
        boton("boton_grafito_lleno", "anillo_rosa", 15, h="match_parent", extra='android:minWidth="44dp" android:layout_marginStart="8dp"'),
    ], orient="horizontal", pad="8dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_anillo_mini", raiz("Contador compacto (2×1, 1×1), diseño Anillo. Tocarlo manda un toque; el ♥ abre la página.", "marco_grafito", [
        '<FrameLayout android:layout_width="42dp" android:layout_height="42dp">',
        grafico("match_parent", "match_parent"),
        corazon_mini("anillo_rosa", extra='android:layout_gravity="center"'),
        "</FrameLayout>",
        col([
            fila([dias(19, "blanco"), etiqueta(19, "blanco", "sans-serif-medium")]),
            fila([horas(11, "anillo_gris"), tv(" h · ", 11, "anillo_gris"), reloj(11, "anillo_gris")]),
        ], extra='android:layout_marginStart="8dp"'),
    ], orient="horizontal", pad="6dp", extra='android:gravity="center" android:contentDescription="Mandar un toque"'))


# ================= TINTA (4) =================
def tinta():
    escribir("layout", "widget_tinta", raiz("Contador completo, diseño Tinta: un solo color y letra delgada.", "marco_tinta", [
        pie("desde el sí · ♥", 12, "tinta_acento", w="match_parent", extra='android:letterSpacing="0.03"'),
        hueco(h="0dp"),
        fila([
            dias(46, "tinta_texto", "sans-serif-light"), tv("d", 18, "tinta_acento", extra='android:layout_marginStart="2dp" android:layout_marginEnd="14dp"'),
            horas(46, "tinta_texto", "sans-serif-light"), tv("h", 18, "tinta_acento", extra='android:layout_marginStart="2dp"'),
            hueco(), reloj(22, "tinta_acento", "sans-serif-light"),
        ], w="match_parent", extra='android:gravity="bottom"'),
        hueco(h="0dp"),
        linea_sep("tinta_borde"),
        fila([meta(12, "tinta_acento", w="0dp", extra='android:layout_weight="1"'),
              boton("boton_tinta", "tinta_acento", 12, texto=COR + " toque", extra='android:paddingHorizontal="12dp" android:paddingVertical="3dp"')],
             w="match_parent", extra='android:gravity="center_vertical" android:layout_marginTop="8dp"'),
    ], pad="16dp"))

    escribir("layout", "widget_tinta_franja", raiz("Contador en franja (3×1, 4×1), diseño Tinta.", "marco_tinta", [
        dias(30, "tinta_texto", "sans-serif-light"), tv("d", 13, "tinta_acento", extra='android:layout_marginStart="2dp" android:layout_marginEnd="10dp"'),
        horas(30, "tinta_texto", "sans-serif-light"), tv("h", 13, "tinta_acento", extra='android:layout_marginStart="2dp"'),
        hueco(h="1dp"),
        reloj(17, "tinta_acento", "sans-serif-light"),
        boton("boton_tinta", "tinta_acento", 15, h="match_parent", extra='android:minWidth="44dp" android:layout_marginStart="10dp"'),
    ], orient="horizontal", pad="10dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_tinta_mini", raiz("Contador compacto (2×1, 1×1), diseño Tinta. Tocarlo manda un toque; el ♥ abre la página.", "marco_tinta", [
        fila([corazon_mini("tinta_acento", 11), tv(" desde el sí", 11, "tinta_acento")]),
        fila([dias(22, "tinta_texto", "sans-serif-light"), tv("d", 11, "tinta_acento", extra='android:layout_marginEnd="6dp"'),
              horas(22, "tinta_texto", "sans-serif-light"), tv("h", 11, "tinta_acento"),
              hueco(h="1dp"), reloj(12, "tinta_acento", "sans-serif-light")],
             w="match_parent", extra='android:gravity="bottom" android:layout_marginTop="2dp"'),
    ], pad="10dp", extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= PAPEL (5) =================
def papel():
    escribir("layout", "widget_papel", raiz("Contador completo, diseño Papel: claro y limpio, como Calendario.", "marco_papel", [
        pie("Rommina ♥ Miguel", 12, "papel_rojo", "sans-serif-medium", w="match_parent", extra=""),
        hueco(h="0dp"),
        fila([dias(52, "papel_texto", "sans-serif-medium"), etiqueta(17, "papel_texto", margen="8dp"), tv(" juntos", 17, "papel_texto")],
             extra='android:gravity="bottom"'),
        hueco(h="0dp"),
        fila([fila([horas(13, "papel_gris"), tv(" h · ", 13, "papel_gris"), reloj(13, "papel_gris")], w="0dp",
                   extra='android:layout_weight="1" android:gravity="center_vertical"'),
              boton("boton_papel", "blanco", 12, texto=COR + " Toque", extra='android:paddingHorizontal="12dp" android:paddingVertical="4dp"')],
             w="match_parent"),
    ], pad="16dp"))

    escribir("layout", "widget_papel_franja", raiz("Contador en franja (3×1, 4×1), diseño Papel.", "marco_papel", [
        dias(36, "papel_texto", "sans-serif-medium"),
        col([fila([etiqueta(13, "papel_texto", "sans-serif-medium", "0dp"), tv(" juntos", 13, "papel_texto", fuente="sans-serif-medium")]),
             fila([horas(12, "papel_gris"), tv(" h · ", 12, "papel_gris"), reloj(12, "papel_gris")], extra='android:layout_marginTop="2dp"')],
            w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"'),
        boton("boton_papel", "blanco", 15, h="match_parent", extra='android:minWidth="44dp"'),
    ], orient="horizontal", pad="10dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_papel_mini", raiz("Contador compacto (2×1, 1×1), diseño Papel. Tocarlo manda un toque; el ♥ abre la página.", "marco_papel", [
        fila([tv("Juntos", 11, "papel_rojo", fuente="sans-serif-medium", w="0dp", extra='android:layout_weight="1"'), corazon_mini("papel_rojo")],
             w="match_parent"),
        fila([dias(26, "papel_texto", "sans-serif-medium"), etiqueta(12, "papel_texto"), hueco(h="1dp"),
              horas(11, "papel_gris"), tv(":", 11, "papel_gris"), reloj(11, "papel_gris")],
             w="match_parent", extra='android:gravity="bottom" android:layout_marginTop="2dp"'),
    ], pad="10dp", extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= BLOQUES (6) =================
def ficha(fondo, etiqueta_xml, numero_xml, peso, pad="10dp", extra=""):
    return col([etiqueta_xml, hueco(w="1dp"), numero_xml], w="0dp", h="match_parent",
               extra=f'android:layout_weight="{peso}" android:background="@drawable/{fondo}" android:padding="{pad}" {extra}')


def bloques():
    escribir("layout", "widget_bloques", raiz("Contador completo, diseño Bloques (One UI): fichas de color.", "marco_bloques", [
        fila([pie("Juntos ♥", 14, "blanco", "sans-serif-black", extra='android:layout_weight="1" android:paddingStart="4dp"'),
              boton("boton_bloques", "blanco", 12, texto=COR + " Toque", extra='android:paddingHorizontal="12dp" android:paddingVertical="4dp" android:textStyle="bold"')],
             w="match_parent"),
        fila([
            ficha("ficha_rosa", tv("días", 11, "bloques_rosa_texto", "dias_etiqueta", "sans-serif-black"), dias(38, "bloques_rosa_texto", "sans-serif-black"), 1.2),
            ficha("ficha_lila", tv("horas", 11, "bloques_lila_texto", "horas_etiqueta", "sans-serif-black"), horas(38, "bloques_lila_texto", "sans-serif-black"), 1,
                  extra='android:layout_marginHorizontal="8dp"'),
            ficha("ficha_menta", tv("min · seg", 11, "bloques_menta_texto", fuente="sans-serif-black"), reloj(24, "bloques_menta_texto", "sans-serif-black"), 1.3),
        ], w="match_parent", h="0dp", extra='android:layout_weight="1" android:layout_marginTop="8dp"'),
    ], pad="10dp"))

    escribir("layout", "widget_bloques_franja", raiz("Contador en franja (3×1, 4×1), diseño Bloques.", "marco_bloques", [
        col([dias(22, "bloques_rosa_texto", "sans-serif-black"), tv("días", 10, "bloques_rosa_texto", "dias_etiqueta", "sans-serif-black")],
            w="0dp", h="match_parent", extra='android:layout_weight="1" android:background="@drawable/ficha_rosa" android:gravity="center"'),
        col([horas(22, "bloques_lila_texto", "sans-serif-black"), tv("horas", 10, "bloques_lila_texto", "horas_etiqueta", "sans-serif-black")],
            w="0dp", h="match_parent", extra='android:layout_weight="1" android:background="@drawable/ficha_lila" android:gravity="center" android:layout_marginHorizontal="5dp"'),
        col([reloj(18, "bloques_menta_texto", "sans-serif-black"), tv("min · seg", 10, "bloques_menta_texto", fuente="sans-serif-black")],
            w="0dp", h="match_parent", extra='android:layout_weight="1.3" android:background="@drawable/ficha_menta" android:gravity="center"'),
        boton("boton_bloques", "blanco", 15, h="match_parent", extra='android:minWidth="42dp" android:layout_marginStart="5dp"'),
    ], orient="horizontal", pad="6dp"))

    escribir("layout", "widget_bloques_mini", raiz("Contador compacto (2×1, 1×1), diseño Bloques. Tocarlo manda un toque; el ♥ abre la página.", "marco_bloques", [
        col([dias(22, "bloques_rosa_texto", "sans-serif-black"),
             fila([tv("días", 10, "bloques_rosa_texto", "dias_etiqueta", "sans-serif-black"), corazon_mini("bloques_rosa_texto", 10, 'android:layout_marginStart="3dp"')])],
            w="0dp", h="match_parent", extra='android:layout_weight="1" android:background="@drawable/ficha_rosa" android:gravity="center"'),
        col([reloj(16, "bloques_lila_texto", "sans-serif-black"),
             fila([horas(10, "bloques_lila_texto", "sans-serif-black"), tv(" h", 10, "bloques_lila_texto", fuente="sans-serif-black")])],
            w="0dp", h="match_parent", extra='android:layout_weight="1.15" android:background="@drawable/ficha_lila" android:gravity="center" android:layout_marginStart="5dp"'),
    ], orient="horizontal", pad="6dp", extra='android:contentDescription="Mandar un toque"'))


# ================= BARRA NOW (7) =================
def barra_now():
    escribir("layout", "widget_barra", raiz("Contador completo, diseño Barra Now (One UI): cápsula negra y barra hacia el próximo mes.", "marco_barra", [
        fila([
            boton("boton_barra", "blanco", 29, w="48dp", h="48dp"),
            col([pie("Juntos desde el sí", 12, "barra_gris", w="match_parent", extra=""),
                 fila([dias(28, "blanco", "sans-serif-black"), etiqueta(28, "blanco", "sans-serif-black", "6dp")])],
                w="0dp", extra='android:layout_weight="1" android:layout_marginStart="12dp"'),
            fila([horas(15, "blanco", "sans-serif-medium"), tv(":", 15, "blanco", fuente="sans-serif-medium"), reloj(15, "blanco", "sans-serif-medium")]),
        ], w="match_parent"),
        barra("6dp", 'android:layout_marginTop="16dp"'),
        fila([tv("este mes", 11, "barra_gris", w="0dp", extra='android:layout_weight="1"'), meta(11, "barra_gris")],
             w="match_parent", extra='android:layout_marginTop="6dp"'),
    ], pad="16dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_barra_franja", raiz("Contador en franja (3×1, 4×1), diseño Barra Now.", "marco_barra", [
        boton("boton_barra", "blanco", 15, w="40dp", h="40dp"),
        col([fila([dias(20, "blanco", "sans-serif-black"), etiqueta(20, "blanco", "sans-serif-black"),
                   hueco(h="1dp"), horas(13, "blanco", "sans-serif-medium"), tv(":", 13, "blanco", fuente="sans-serif-medium"), reloj(13, "blanco", "sans-serif-medium")],
                  w="match_parent", extra='android:gravity="bottom"'),
             barra("4dp", 'android:layout_marginTop="5dp"'),
             meta(10, "barra_gris", 'android:layout_marginTop="3dp"')],
            w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"'),
    ], orient="horizontal", pad="8dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_barra_mini", raiz("Contador compacto (2×1, 1×1), diseño Barra Now. Tocarlo manda un toque; el ♥ abre la página.", "marco_barra", [
        corazon_mini("blanco", 12, 'android:gravity="center" android:background="@drawable/boton_barra"', w="40dp", h="40dp"),
        col([fila([dias(19, "blanco", "sans-serif-black"), etiqueta(19, "blanco", "sans-serif-black")]),
             fila([horas(11, "barra_gris"), tv(" h · ", 11, "barra_gris"), reloj(11, "barra_gris")])],
            extra='android:layout_marginStart="10dp"'),
    ], orient="horizontal", pad="6dp", extra='android:gravity="center" android:contentDescription="Mandar un toque"'))


# ================= PASTEL (8) =================
def chip(xml):
    return fila(xml, extra='android:background="@drawable/chip_pastel" android:paddingHorizontal="10dp" android:paddingVertical="3dp" android:layout_marginEnd="6dp" android:gravity="center_vertical"')


def pastel():
    escribir("layout", "widget_pastel", raiz("Contador completo, diseño Pastel (One UI): lila claro y número grueso.", "marco_pastel", [
        fila([pie("♥ Rommina y Miguel", 13, "pastel_morado", "sans-serif-black"),
              boton("boton_pastel", "blanco", 19, w="34dp", h="34dp")], w="match_parent"),
        hueco(h="0dp"),
        fila([dias(56, "pastel_tinta", "sans-serif-black"), etiqueta(18, "pastel_tinta", "sans-serif-black", "8dp")], extra='android:gravity="bottom"'),
        hueco(h="0dp"),
        fila([chip([horas(12, "pastel_tinta", "sans-serif-black"), tv(" h", 12, "pastel_tinta", fuente="sans-serif-black")]),
              chip([reloj(12, "pastel_tinta", "sans-serif-black")]),
              meta(11, "pastel_morado", w="0dp", extra='android:layout_weight="1"')], w="match_parent"),
    ], pad="16dp"))

    escribir("layout", "widget_pastel_franja", raiz("Contador en franja (3×1, 4×1), diseño Pastel.", "marco_pastel", [
        dias(36, "pastel_tinta", "sans-serif-black"),
        col([etiqueta(14, "pastel_tinta", "sans-serif-black", "0dp"),
             fila([horas(12, "pastel_morado", "sans-serif-medium"), tv(" h · ", 12, "pastel_morado", fuente="sans-serif-medium"), reloj(12, "pastel_morado", "sans-serif-medium")],
                  extra='android:layout_marginTop="2dp"')],
            w="0dp", extra='android:layout_weight="1" android:layout_marginStart="10dp"'),
        boton("boton_pastel", "blanco", 15, w="40dp", h="40dp"),
    ], orient="horizontal", pad="10dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_pastel_mini", raiz("Contador compacto (2×1, 1×1), diseño Pastel. Tocarlo manda un toque; el ♥ abre la página.", "marco_pastel", [
        fila([corazon_mini("pastel_morado", 11), tv(" Juntos", 11, "pastel_morado", fuente="sans-serif-black")]),
        fila([dias(27, "pastel_tinta", "sans-serif-black"), etiqueta(12, "pastel_tinta", "sans-serif-black"), hueco(h="1dp"),
              reloj(11, "pastel_morado", "sans-serif-medium")], w="match_parent", extra='android:gravity="bottom" android:layout_marginTop="2dp"'),
    ], pad="10dp", extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= CORAL (9) =================
def coral():
    escribir("layout", "widget_coral", raiz("Contador completo, diseño Coral (One UI): color fuerte y letras blancas.", "marco_coral", [
        col([pie("Juntos desde el sí", 13, "blanco", "sans-serif-black", w="match_parent", extra=""),
             hueco(w="1dp"),
             fila([dias(54, "blanco", "sans-serif-black"), etiqueta(18, "blanco", "sans-serif-black", "6dp")], extra='android:gravity="bottom"'),
             hueco(w="1dp"),
             meta(12, "coral_crema")],
            w="0dp", h="match_parent", extra='android:layout_weight="1"'),
        col([col([fila([horas(20, "blanco", "sans-serif-black"), tv(" h", 20, "blanco", fuente="sans-serif-black")]),
                  reloj(14, "blanco", "sans-serif-medium")],
                 w="match_parent", h="0dp", extra='android:layout_weight="1" android:background="@drawable/caja_coral" android:gravity="center"'),
             boton("boton_coral", "coral_fondo", 13, texto=COR + " Toque", w="match_parent", h="36dp", extra='android:layout_marginTop="6dp" android:textStyle="bold"')],
            w="96dp", h="match_parent", extra='android:layout_marginStart="12dp"'),
    ], orient="horizontal", pad="14dp"))

    escribir("layout", "widget_coral_franja", raiz("Contador en franja (3×1, 4×1), diseño Coral.", "marco_coral", [
        fila([dias(34, "blanco", "sans-serif-black"), etiqueta(14, "blanco", "sans-serif-black", "6dp")], extra=""),
        hueco(h="1dp"),
        col([fila([horas(15, "blanco", "sans-serif-black"), tv(" h", 15, "blanco", fuente="sans-serif-black")]), reloj(12, "blanco", "sans-serif-medium")],
            h="match_parent", extra='android:background="@drawable/caja_coral" android:gravity="center" android:paddingHorizontal="12dp"'),
        boton("boton_coral", "coral_fondo", 15, h="match_parent", extra='android:minWidth="42dp" android:layout_marginStart="6dp"'),
    ], orient="horizontal", pad="8dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_coral_mini", raiz("Contador compacto (2×1, 1×1), diseño Coral. Tocarlo manda un toque; el ♥ abre la página.", "marco_coral", [
        fila([tv("Juntos", 11, "blanco", fuente="sans-serif-black", w="0dp", extra='android:layout_weight="1"'), corazon_mini("blanco")], w="match_parent"),
        fila([dias(27, "blanco", "sans-serif-black"), etiqueta(12, "blanco", "sans-serif-black"), hueco(h="1dp"), reloj(11, "coral_crema", "sans-serif-medium")],
             w="match_parent", extra='android:gravity="bottom" android:layout_marginTop="2dp"'),
    ], pad="10dp", extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= VIDRIO LÍQUIDO (10) =================
def vidrio():
    # Todo en proporción áurea (8 · 13 · 21 · 34 · 55): letras, botones, huecos y anchos.
    escribir("layout", "widget_vidrio", raiz("Contador completo, diseño Vidrio líquido: translúcido con el borde iluminado arriba. Medidas en proporción áurea.", "marco_vidrio", [
        fila([pie("♥ Juntos", 13, "vidrio_suave"),
              fila([horas(13, "vidrio_suave"), tv(" h · ", 13, "vidrio_suave"), reloj(13, "vidrio_suave")])], w="match_parent"),
        hueco(h="0dp", peso=1),
        # 55 : 21 = φ²
        fila([dias(55, "blanco", "sans-serif-medium"), etiqueta(21, "blanco", margen="8dp")], extra='android:baselineAligned="true"'),
        hueco(h="0dp", peso=1.618),
        # Botones de 34 dp de alto; «Toque» (el principal) mide φ veces «Abrir página».
        fila([tv("Abrir página", 13, "blanco", w="0dp", h="34dp", extra='android:layout_weight="1" android:gravity="center" android:background="@drawable/boton_vidrio" android:layout_marginEnd="8dp"'),
              boton("boton_vidrio", "vidrio_rosa", 13, texto=COR + " Toque", w="0dp", h="34dp", extra='android:layout_weight="1.618"')],
             w="match_parent"),
    ]))

    escribir("layout", "widget_vidrio_franja", raiz("Contador en franja (3×1, 4×1), diseño Vidrio líquido. Medidas en proporción áurea.", "marco_vidrio", [
        dias(34, "blanco", "sans-serif-medium"),
        # «días» (21) + reloj (13) = 34, la altura del número.
        col([etiqueta(21, "blanco", margen="0dp"),
             fila([horas(13, "vidrio_suave"), tv(" h · ", 13, "vidrio_suave"), reloj(13, "vidrio_suave")])],
            w="0dp", extra='android:layout_weight="1" android:layout_marginStart="8dp"'),
        # Botón de 34 × 55 dp (1 : φ).
        boton("boton_vidrio", "vidrio_rosa", 21, w="34dp", h="55dp", extra='android:layout_marginStart="13dp"'),
    ], orient="horizontal", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_vidrio_mini", raiz("Contador compacto (2×1, 1×1), diseño Vidrio líquido. Tocarlo manda un toque; el ♥ abre la página.", "marco_vidrio", [
        fila([corazon_mini("vidrio_rosa", 13), tv(" Juntos · ", 13, "vidrio_suave"), horas(13, "vidrio_suave"), tv(" h", 13, "vidrio_suave")]),
        # 34 : 13 = φ²
        fila([dias(34, "blanco", "sans-serif-medium"), etiqueta(13, "blanco"), hueco(h="1dp"), reloj(13, "vidrio_suave")],
             w="match_parent", extra='android:baselineAligned="true"'),
    ], extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= TRANSPARENTE (12) =================
def transparente():
    s = SOMBRA
    escribir("layout", "widget_transparente", raiz("Contador completo, diseño Transparente: solo letras con sombra sobre el fondo de pantalla.", None, [
        fila([pie("♥ Rommina y Miguel", 13, "blanco", extra=f'android:layout_weight="1" {s}'),
              boton("boton_transparente", "blanco", 24, w="40dp", h="40dp", extra=s)], w="match_parent"),
        hueco(h="0dp"),
        fila([tv("38", 42, "blanco", "dias", "sans-serif-black", extra=s), tv("días", 42, "blanco", "dias_etiqueta", "sans-serif-black", extra=f'android:layout_marginStart="10dp" {s}')]),
        fila([tv("04", 15, "blanco", "horas", extra=s), tv(" h · ", 15, "blanco", extra=s), reloj(15, "blanco", extra=s),
              tv(" · bajo las mismas estrellas", 15, "blanco", w="0dp", extra=f'android:layout_weight="1" android:ellipsize="end" {s}')],
             w="match_parent", extra='android:layout_marginTop="2dp" android:layout_marginBottom="8dp"'),
    ], pad="14dp"))

    escribir("layout", "widget_transparente_franja", raiz("Contador en franja (3×1, 4×1), diseño Transparente.", None, [
        col([fila([tv("38", 28, "blanco", "dias", "sans-serif-black", extra=s), tv("días", 28, "blanco", "dias_etiqueta", "sans-serif-black", extra=f'android:layout_marginStart="6dp" {s}')]),
             fila([tv("04", 13, "blanco", "horas", extra=s), tv(" h · ", 13, "blanco", extra=s), reloj(13, "blanco", extra=s)], extra='android:layout_marginTop="2dp"')],
            w="0dp", extra='android:layout_weight="1"'),
        boton("boton_transparente", "blanco", 15, w="44dp", h="44dp", extra=s),
    ], orient="horizontal", pad="8dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_transparente_mini", raiz("Contador compacto (2×1, 1×1), diseño Transparente. Tocarlo manda un toque; el ♥ abre la página.", None, [
        fila([corazon_mini("blanco", 11, s), tv(" ", 11, "blanco"), tv("38", 11, "blanco", "dias", extra=s), tv("días", 11, "blanco", "dias_etiqueta", extra=f'android:layout_marginStart="3dp" {s}')]),
        fila([tv("04", 22, "blanco", "horas", "sans-serif-black", extra=s), tv(":", 22, "blanco", fuente="sans-serif-black", extra=s), reloj(22, "blanco", "sans-serif-black", s)]),
    ], pad="8dp", extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= PUNTOS (13) =================
def puntos():
    escribir("layout", "widget_puntos", raiz("Contador completo, diseño Puntos: un punto por cada día del mes juntos; el de hoy va en blanco.", "marco_grafito", [
        fila([dias(30, "blanco", "sans-serif-medium"), etiqueta(14, "puntos_gris", margen="6dp"), tv(" juntos", 14, "puntos_gris"), hueco(h="1dp"),
              horas(13, "puntos_rosa", "sans-serif-medium"), tv(":", 13, "puntos_rosa", fuente="sans-serif-medium"), reloj(13, "puntos_rosa", "sans-serif-medium")],
             w="match_parent", extra='android:gravity="bottom"'),
        grafico("match_parent", "0dp", 'android:layout_weight="1" android:layout_marginVertical="8dp"'),
        fila([pie("", 11, "puntos_gris", extra='android:layout_weight="1" android:visibility="gone"'),
              meta(11, "puntos_gris", w="0dp", extra='android:layout_weight="1"'),
              boton("boton_grafito_lleno", "puntos_rosa", 16, extra='android:paddingHorizontal="12dp" android:paddingVertical="2dp"')],
             w="match_parent"),
    ], pad="14dp"))

    escribir("layout", "widget_puntos_franja", raiz("Contador en franja (3×1, 4×1), diseño Puntos.", "marco_grafito", [
        col([dias(24, "blanco", "sans-serif-medium"),
             fila([horas(11, "puntos_rosa"), tv(":", 11, "puntos_rosa"), reloj(11, "puntos_rosa")])]),
        grafico("0dp", "match_parent", 'android:layout_weight="1" android:layout_marginHorizontal="10dp"'),
        boton("boton_grafito_lleno", "puntos_rosa", 15, h="match_parent", extra='android:minWidth="42dp"'),
    ], orient="horizontal", pad="8dp", extra='android:gravity="center_vertical"'))

    escribir("layout", "widget_puntos_mini", raiz("Contador compacto (2×1, 1×1), diseño Puntos. Tocarlo manda un toque; el ♥ abre la página.", "marco_grafito", [
        col([dias(22, "blanco", "sans-serif-medium"),
             fila([corazon_mini("puntos_rosa", 10), tv(" ", 10, "puntos_gris"), reloj(10, "puntos_gris")])]),
        grafico("0dp", "match_parent", 'android:layout_weight="1" android:layout_marginStart="8dp"'),
    ], orient="horizontal", pad="8dp", extra='android:gravity="center_vertical" android:contentDescription="Mandar un toque"'))


# ================= drawables =================
def forma(solido=None, borde=None, ancho="1dp", radio="@dimen/radio_widget", forma_=None):
    s = f'<solid android:color="@color/{solido}" />' if solido else ""
    b = f'<stroke android:width="{ancho}" android:color="@color/{borde}" />' if borde else ""
    c = f'<corners android:radius="{radio}" />' if radio else ""
    f = f' android:shape="{forma_}"' if forma_ else ""
    return f"<shape{f}>{s}{b}{c}</shape>"


def marco(nombre, comentario, solido, borde=None):
    escribir("drawable", nombre, f"<!-- {comentario} -->\n"
             + forma(solido, borde).replace("<shape>", f"<shape {A}>", 1))


def selector(nombre, comentario, normal, presionado):
    escribir("drawable", nombre, f"""<!-- {comentario} Se marca al instante al presionar y se suelta con un fundido corto. -->
<selector {A} android:exitFadeDuration="150">
    <item android:state_pressed="true">{presionado}</item>
    <item>{normal}</item>
</selector>""")


def drawables():
    marco("marco_grafito", "Anillo y Puntos: gris grafito como los widgets de Apple.", "grafito_fondo")
    marco("marco_tinta", "Tinta: azul noche con un filete del mismo tono.", "tinta_fondo", "tinta_borde")
    marco("marco_papel", "Papel: blanco hueso, como Calendario.", "papel_fondo")
    marco("marco_bloques", "Bloques: tarjeta oscura para las fichas de color.", "bloques_fondo")
    marco("marco_barra", "Barra Now: cápsula negra.", "barra_fondo")
    marco("marco_pastel", "Pastel: lila claro.", "pastel_fondo")
    marco("marco_coral", "Coral: color fuerte de One UI.", "coral_fondo")
    escribir("drawable", "marco_vidrio", f"""<!-- Vidrio líquido: velo translúcido, un brillo suave que baja desde el borde de arriba
     (como luz sobre el vidrio) y un filete claro. Un widget no puede desenfocar el fondo de pantalla. -->
<layer-list {A}>
    <item>{forma("vidrio_velo", "vidrio_borde")}</item>
    <item android:left="1dp" android:top="1dp" android:right="1dp" android:bottom="1dp"><shape><gradient android:angle="270" android:type="linear" android:startColor="@color/vidrio_brillo" android:centerColor="#00FFFFFF" android:endColor="#00FFFFFF" android:centerY="0.35" /><corners android:radius="@dimen/radio_widget" /></shape></item>
</layer-list>""")
    for n, c in [("ficha_rosa", "bloques_rosa"), ("ficha_lila", "bloques_lila"), ("ficha_menta", "bloques_menta")]:
        escribir("drawable", n, f"<!-- Ficha de color del diseño Bloques. -->\n" + forma(c, radio="18dp").replace("<shape>", f"<shape {A}>", 1))
    escribir("drawable", "chip_pastel", "<!-- Pastilla blanca del diseño Pastel. -->\n" + forma("blanco", radio="99dp").replace("<shape>", f"<shape {A}>", 1))
    escribir("drawable", "caja_coral", "<!-- Panel translúcido sobre el coral. -->\n" + forma("coral_panel", radio="18dp").replace("<shape>", f"<shape {A}>", 1))

    selector("boton_grafito", "♥ en el centro del anillo: sin fondo hasta que se presiona.",
             forma("transparente", radio=None, forma_="oval"), forma("grafito_presionado", radio=None, forma_="oval"))
    selector("boton_grafito_lleno", "Botón ♥ de Anillo y Puntos.",
             forma("grafito_boton", radio="99dp"), forma("grafito_presionado", radio="99dp"))
    selector("boton_tinta", "Botón ♥ de Tinta.",
             forma("transparente", "tinta_acento", radio="99dp"), forma("tinta_borde", "tinta_acento", radio="99dp"))
    selector("boton_papel", "Botón ♥ Toque de Papel.", forma("papel_rojo", radio="99dp"), forma("papel_rojo_presionado", radio="99dp"))
    selector("boton_bloques", "Botón ♥ Toque de Bloques.", forma("bloques_boton", radio="99dp"), forma("bloques_boton_presionado", radio="99dp"))
    selector("boton_barra", "Círculo ♥ de Barra Now.", forma("barra_rosa", radio=None, forma_="oval"), forma("barra_rosa_presionado", radio=None, forma_="oval"))
    selector("boton_pastel", "Círculo ♥ de Pastel.", forma("pastel_morado", radio=None, forma_="oval"), forma("pastel_morado_presionado", radio=None, forma_="oval"))
    selector("boton_coral", "Botón ♥ Toque de Coral.", forma("blanco", radio="18dp"), forma("coral_crema", radio="18dp"))
    selector("boton_vidrio", "Pastillas de vidrio.", forma("vidrio_pastilla", "vidrio_borde", radio="99dp"), forma("vidrio_presionado", "vidrio_filo", radio="99dp"))
    selector("boton_transparente", "♥ del diseño Transparente.",
             forma("transparente", radio=None, forma_="oval"), forma("vidrio_presionado", radio=None, forma_="oval"))
    escribir("drawable", "barra_progreso", f"""<!-- Barra Now: pista gris y avance rosa hacia el próximo mes (redondeado en las dos puntas). -->
<layer-list {A}>
    <item android:id="@android:id/background">{forma("barra_pista", radio="99dp")}</item>
    <item android:id="@android:id/progress"><scale android:scaleWidth="100%">{forma("barra_rosa", radio="99dp")}</scale></item>
</layer-list>""")


for f in (anillo, tinta, papel, bloques, barra_now, pastel, coral, vidrio, transparente, puntos, drawables):
    f()
print("listo")
