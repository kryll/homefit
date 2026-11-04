#!/usr/bin/env python3
"""Generador de ilustraciones propias para HomeFit: figura de palitos, 2 fotogramas (inicio/fin).
Cada ejercicio: dict con dos poses; cada pose: articulaciones en rejilla 100x100 (y hacia abajo).
Vista lateral por defecto. Suelo en y=88."""
import json, math, pathlib
from PIL import Image, ImageDraw

INK = (26, 43, 74)       # figura
GROUND = (120, 130, 145)
EQUIP = (90, 105, 130)
W = 480
SC = W / 100.0

def P(x, y): return (x * SC, y * SC)

def draw_fig(dr, j, lw=7):
    def seg(a, b, w=lw):
        dr.line([P(*a), P(*b)], fill=INK, width=int(w * SC), joint='curve')
    def dot(a, r=2.6):
        x, y = P(*a); rr = r * SC
        dr.ellipse([x - rr, y - rr, x + rr, y + rr], fill=INK)
    # cabeza
    hx, hy = P(*j['head']); hr = 5.2 * SC
    dr.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], outline=INK, width=int(3.2 * SC))
    # torso (con posible curvatura)
    if 'spineMid' in j:
        seg(j['neck'], j['spineMid']); seg(j['spineMid'], j['hip'])
    else:
        seg(j['neck'], j['hip'])
    seg(j['neck'], j['shoulder']) if False else None
    # brazo (y segundo brazo opcional)
    seg(j['shoulder'], j['elbow']); seg(j['elbow'], j['hand'])
    if 'elbow2' in j:
        seg(j['shoulder'], j['elbow2']); seg(j['elbow2'], j['hand2'])
    # pierna (y segunda pierna opcional)
    seg(j['hip'], j['knee']); seg(j['knee'], j['foot'])
    if 'knee2' in j:
        seg(j['hip'], j['knee2']); seg(j['knee2'], j['foot2'])
    # articulaciones
    for k in ('shoulder', 'elbow', 'hip', 'knee', 'elbow2', 'knee2'):
        if k in j: dot(j[k])
    dr.line([P(*j['neck']), P(*j['shoulder'])], fill=INK, width=int(5 * SC))  # cuello-hombro

def draw_ground(dr):
    dr.line([P(4, 88), P(96, 88)], fill=GROUND, width=int(1.2 * SC))

def draw_equip(dr, items):
    for it in items:
        k = it[0]
        if k == 'bench':  # banco/silla baja: (x, y, w, h)
            _, x, y, w, h = it
            dr.rectangle([P(x, y), P(x + w, y + h)], outline=EQUIP, width=int(2.4 * SC))
            dr.line([P(x + 1, y + h), P(x + 1, 88)], fill=EQUIP, width=int(1.8 * SC))
            dr.line([P(x + w - 1, y + h), P(x + w - 1, 88)], fill=EQUIP, width=int(1.8 * SC))
        elif k == 'chair':  # silla con respaldo a la izquierda
            _, x, y = it
            dr.rectangle([P(x, y), P(x + 14, y + 4)], outline=EQUIP, width=int(2.4 * SC))
            dr.line([P(x + 1, y + 4), P(x + 1, 88)], fill=EQUIP, width=int(1.8 * SC))
            dr.line([P(x + 13, y + 4), P(x + 13, 88)], fill=EQUIP, width=int(1.8 * SC))
            dr.line([P(x + 1, y), P(x + 1, y - 16)], fill=EQUIP, width=int(2 * SC))
        elif k == 'wall':
            _, x = it
            dr.line([P(x, 8), P(x, 88)], fill=EQUIP, width=int(2 * SC))
        elif k == 'kb':  # kettlebell/mancuerna en la mano
            _, x, y = it
            cx, cy = P(x, y); r = 3.4 * SC
            dr.ellipse([cx - r, cy - r, cx + r, cy + r], fill=EQUIP)
        elif k == 'band':  # banda: arco entre dos puntos
            _, a, b = it
            dr.line([P(*a), P(*b)], fill=(60, 130, 90), width=int(1.6 * SC))

POSES = {
  # flexiones con rodillas apoyadas (vista lateral, cabeza a la izquierda)
  'flexiones_rodillas': [
    dict(head=(26, 40), neck=(30, 47), shoulder=(33, 50), elbow=(35, 66), hand=(36, 84),
         hip=(58, 66), knee=(74, 84), foot=(90, 84), spineMid=(45, 55)),
    dict(head=(26, 54), neck=(30, 60), shoulder=(33, 63), elbow=(24, 68), hand=(36, 84),
         hip=(56, 74), knee=(74, 84), foot=(90, 84), spineMid=(44, 66)),
  ],
  # gato-camello (cuadrupedia; 0 = camello: espalda hundida; 1 = gato: espalda arqueada)
  'gato_camello': [
    dict(head=(24, 50), neck=(29, 53), shoulder=(36, 55), elbow=(37, 70), hand=(37, 86),
         hip=(64, 56), knee=(64, 86), foot=(78, 86), spineMid=(50, 66)),
    dict(head=(24, 62), neck=(29, 60), shoulder=(36, 56), elbow=(37, 70), hand=(37, 86),
         hip=(64, 58), knee=(64, 86), foot=(78, 86), spineMid=(50, 44)),
  ],
  # sentadilla a silla (0 = de pie; 1 = sentado tocando la silla)
  'sentadilla_silla': [
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(51, 42), hand=(55, 54),
         hip=(46, 56), knee=(49, 70), foot=(52, 86)),
    dict(head=(40, 34), neck=(40, 42), shoulder=(41, 46), elbow=(51, 52), hand=(59, 54),
         hip=(40, 66), knee=(56, 66), foot=(54, 86)),
  ],
  # fondos en banco (manos en el banco detras)
  'fondos_banco': [
    dict(head=(50, 34), neck=(48, 42), shoulder=(45, 46), elbow=(41, 56), hand=(37, 66),
         hip=(56, 60), knee=(72, 68), foot=(88, 84)),
    dict(head=(52, 44), neck=(50, 52), shoulder=(47, 56), elbow=(38, 50), hand=(37, 66),
         hip=(56, 70), knee=(72, 76), foot=(88, 84)),
  ],
  'fondos_banco_rodillas': [
    dict(head=(50, 34), neck=(48, 42), shoulder=(45, 46), elbow=(41, 56), hand=(37, 66),
         hip=(54, 60), knee=(70, 70), foot=(70, 86)),
    dict(head=(52, 44), neck=(50, 52), shoulder=(47, 56), elbow=(38, 50), hand=(37, 66),
         hip=(54, 70), knee=(70, 78), foot=(70, 86)),
  ],
  # pike push-up (V invertida)
  'pike_push_up': [
    dict(head=(27, 62), neck=(30, 58), shoulder=(33, 60), elbow=(34, 73), hand=(34, 86),
         hip=(55, 38), knee=(62, 62), foot=(70, 86)),
    dict(head=(27, 74), neck=(30, 68), shoulder=(33, 70), elbow=(25, 74), hand=(34, 86),
         hip=(55, 38), knee=(62, 62), foot=(70, 86)),
  ],
  # zancada hacia delante (0 = de pie; 1 = zancada abajo)
  'zancada': [
    dict(head=(48, 16), neck=(48, 24), shoulder=(48, 28), elbow=(53, 42), hand=(56, 54),
         hip=(48, 56), knee=(50, 70), foot=(52, 86)),
    dict(head=(50, 34), neck=(50, 42), shoulder=(50, 46), elbow=(55, 58), hand=(58, 68),
         hip=(50, 62), knee=(64, 66), foot=(64, 86), knee2=(42, 76), foot2=(34, 84)),
  ],
  # zancada hacia atras
  'zancada_atras': [
    dict(head=(48, 16), neck=(48, 24), shoulder=(48, 28), elbow=(53, 42), hand=(56, 54),
         hip=(48, 56), knee=(50, 70), foot=(52, 86)),
    dict(head=(46, 34), neck=(46, 42), shoulder=(46, 46), elbow=(51, 58), hand=(54, 68),
         hip=(46, 62), knee=(58, 68), foot=(58, 86), knee2=(36, 70), foot2=(30, 86)),
  ],
  # subida al banco
  'subida_banco': [
    dict(head=(42, 24), neck=(42, 32), shoulder=(42, 36), elbow=(46, 50), hand=(48, 60),
         hip=(42, 60), knee=(44, 74), foot=(44, 86), knee2=(54, 62), foot2=(65, 66)),
    dict(head=(62, 8), neck=(62, 16), shoulder=(62, 20), elbow=(66, 34), hand=(68, 46),
         hip=(62, 44), knee=(63, 56), foot=(64, 66), knee2=(72, 52), foot2=(76, 62)),
  ],
  # plancha con rodillas apoyadas (isometrica: dos fotogramas iguales)
  'plancha_rodillas': [
    dict(head=(24, 46), neck=(28, 52), shoulder=(31, 55), elbow=(33, 84), hand=(45, 84),
         hip=(56, 66), knee=(72, 84), foot=(88, 84), spineMid=(44, 58)),
    dict(head=(24, 46), neck=(28, 52), shoulder=(31, 55), elbow=(33, 84), hand=(45, 84),
         hip=(56, 66), knee=(72, 84), foot=(88, 84), spineMid=(44, 58)),
  ],
  # elevacion de gemelos (0 = pie plano; 1 = de puntillas)
  'elevacion_gemelos': [
    dict(head=(46, 18), neck=(46, 26), shoulder=(46, 30), elbow=(50, 44), hand=(53, 56),
         hip=(46, 58), knee=(47, 72), foot=(46, 86)),
    dict(head=(46, 12), neck=(46, 20), shoulder=(46, 24), elbow=(50, 38), hand=(53, 50),
         hip=(46, 52), knee=(49, 70), foot=(42, 84)),
  ],
  # estiramiento de isquiotibiales sentado
  'estiramiento_isquios': [
    dict(head=(34, 46), neck=(34, 54), shoulder=(35, 58), elbow=(42, 70), hand=(48, 80),
         hip=(36, 84), knee=(56, 82), foot=(74, 84)),
    dict(head=(54, 58), neck=(48, 62), shoulder=(46, 66), elbow=(58, 72), hand=(72, 80),
         hip=(36, 84), knee=(56, 82), foot=(74, 84)),
  ],
  # jumping jacks (0 = pies juntos brazos abajo; 1 = piernas abiertas brazos en V)
  'jumping_jacks': [
    dict(head=(48, 18), neck=(48, 26), shoulder=(48, 30), elbow=(50, 44), hand=(51, 58),
         hip=(48, 58), knee=(48, 72), foot=(47, 86)),
    dict(head=(48, 16), neck=(48, 24), shoulder=(48, 28), elbow=(40, 20), hand=(32, 10),
         elbow2=(56, 20), hand2=(64, 10),
         hip=(48, 56), knee=(42, 70), foot=(38, 86), knee2=(54, 70), foot2=(60, 86)),
  ],
  # rodillas altas (0 = de pie; 1 = rodilla arriba)
  'rodillas_altas': [
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(50, 42), hand=(53, 54),
         hip=(46, 56), knee=(48, 70), foot=(50, 86)),
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(54, 36), hand=(60, 40),
         elbow2=(40, 40), hand2=(36, 52),
         hip=(46, 56), knee=(58, 56), foot=(56, 70), knee2=(44, 70), foot2=(44, 86)),
  ],
  # burpees (0 = de pie; 1 = plancha)
  'burpees': [
    dict(head=(48, 16), neck=(48, 24), shoulder=(48, 28), elbow=(53, 42), hand=(56, 54),
         hip=(48, 56), knee=(50, 70), foot=(52, 86)),
    dict(head=(26, 42), neck=(30, 48), shoulder=(33, 51), elbow=(35, 68), hand=(36, 86),
         hip=(58, 62), knee=(72, 76), foot=(88, 82), spineMid=(45, 54)),
  ],
  # burpees sin salto (0 = sentadilla profunda manos al suelo; 1 = plancha)
  'burpees_sin_salto': [
    dict(head=(42, 44), neck=(42, 52), shoulder=(43, 56), elbow=(46, 70), hand=(46, 84),
         hip=(46, 68), knee=(60, 66), foot=(56, 86)),
    dict(head=(26, 42), neck=(30, 48), shoulder=(33, 51), elbow=(35, 68), hand=(36, 86),
         hip=(58, 62), knee=(72, 76), foot=(88, 82), spineMid=(45, 54)),
  ],
  # russian twist (sentado en V; 0 = manos a un lado; 1 = al otro)
  'russian_twist': [
    dict(head=(44, 40), neck=(42, 48), shoulder=(42, 52), elbow=(34, 60), hand=(28, 66),
         elbow2=(35, 63), hand2=(29, 69),
         hip=(50, 80), knee=(64, 68), foot=(74, 78)),
    dict(head=(44, 40), neck=(42, 48), shoulder=(42, 52), elbow=(52, 60), hand=(60, 66),
         elbow2=(51, 63), hand2=(59, 69),
         hip=(50, 80), knee=(64, 68), foot=(74, 78)),
  ],
  # dead bug (tumbado boca arriba; 0 = brazos arriba y piernas en mesa; 1 = brazo y pierna contrarios extendidos)
  'dead_bug': [
    dict(head=(20, 78), neck=(26, 80), shoulder=(30, 79), elbow=(30, 62), hand=(30, 50),
         hip=(54, 80), knee=(56, 62), foot=(70, 64)),
    dict(head=(20, 78), neck=(26, 80), shoulder=(30, 79), elbow=(18, 72), hand=(8, 78),
         hip=(54, 80), knee=(68, 74), foot=(84, 82)),
  ],
  # bird dog (cuadrupedia; 1 = brazo y pierna contrarios extendidos)
  'bird_dog': [
    dict(head=(24, 52), neck=(29, 54), shoulder=(36, 55), elbow=(37, 70), hand=(37, 86),
         hip=(64, 56), knee=(64, 86), foot=(76, 86), spineMid=(50, 56)),
    dict(head=(16, 46), neck=(23, 50), shoulder=(36, 54), elbow=(26, 46), hand=(12, 42),
         hip=(64, 56), knee=(78, 56), foot=(90, 52), spineMid=(50, 55)),
  ],
  # pajaros con banda (0 = brazos al frente juntos; 1 = brazos abiertos, banda estirada)
  'pajaros': [
    dict(head=(44, 18), neck=(44, 26), shoulder=(44, 30), elbow=(54, 31), hand=(64, 32),
         elbow2=(53, 35), hand2=(63, 36),
         hip=(44, 58), knee=(45, 72), foot=(47, 86)),
    dict(head=(44, 18), neck=(44, 26), shoulder=(44, 30), elbow=(56, 36), hand=(70, 42),
         elbow2=(32, 36), hand2=(20, 42),
         hip=(44, 58), knee=(45, 72), foot=(47, 86)),
  ],
  # remo con banda sentado (banda en los pies)
  'remo_banda': [
    dict(head=(40, 48), neck=(40, 56), shoulder=(41, 60), elbow=(51, 66), hand=(61, 72),
         hip=(42, 84), knee=(60, 80), foot=(76, 84)),
    dict(head=(42, 46), neck=(42, 54), shoulder=(43, 58), elbow=(34, 68), hand=(38, 74),
         hip=(42, 84), knee=(60, 80), foot=(76, 84)),
  ],
  # jalon con banda (anclaje alto, 0 = brazos arriba; 1 = tirar al pecho)
  'jalon_banda': [
    dict(head=(42, 26), neck=(42, 34), shoulder=(42, 38), elbow=(48, 22), hand=(56, 8),
         hip=(42, 64), knee=(44, 76), foot=(46, 86)),
    dict(head=(42, 28), neck=(42, 36), shoulder=(42, 40), elbow=(48, 50), hand=(44, 44),
         hip=(42, 64), knee=(44, 76), foot=(46, 86)),
  ],
  # curl de biceps con banda (pisando la banda)
  'curl_banda': [
    dict(head=(44, 18), neck=(44, 26), shoulder=(44, 30), elbow=(45, 44), hand=(46, 58),
         hip=(44, 58), knee=(45, 72), foot=(44, 86)),
    dict(head=(44, 18), neck=(44, 26), shoulder=(44, 30), elbow=(45, 46), hand=(40, 30),
         hip=(44, 58), knee=(45, 72), foot=(44, 86)),
  ],
  # face pull con banda (anclaje a la altura de la cara)
  'face_pull_banda': [
    dict(head=(38, 22), neck=(38, 30), shoulder=(38, 34), elbow=(50, 34), hand=(62, 32),
         hip=(38, 62), knee=(39, 74), foot=(41, 86)),
    dict(head=(38, 22), neck=(38, 30), shoulder=(38, 34), elbow=(50, 22), hand=(42, 28),
         elbow2=(48, 26), hand2=(40, 31),
         hip=(38, 62), knee=(39, 74), foot=(41, 86)),
  ],
  # press de pecho con banda (banda a la espalda)
  'press_pecho_banda': [
    dict(head=(44, 20), neck=(44, 28), shoulder=(44, 32), elbow=(40, 46), hand=(52, 40),
         hip=(44, 60), knee=(45, 72), foot=(47, 86)),
    dict(head=(44, 20), neck=(44, 28), shoulder=(44, 32), elbow=(58, 34), hand=(72, 36),
         hip=(44, 60), knee=(45, 72), foot=(47, 86)),
  ],
  # aperturas con banda (0 = brazos abiertos; 1 = manos juntas al frente)
  'aperturas_banda': [
    dict(head=(44, 18), neck=(44, 26), shoulder=(44, 30), elbow=(56, 36), hand=(68, 44),
         elbow2=(32, 36), hand2=(22, 44),
         hip=(44, 58), knee=(45, 72), foot=(47, 86)),
    dict(head=(44, 18), neck=(44, 26), shoulder=(44, 30), elbow=(54, 32), hand=(64, 36),
         elbow2=(53, 36), hand2=(63, 40),
         hip=(44, 58), knee=(45, 72), foot=(47, 86)),
  ],
  # pallof press (anclaje lateral; 0 = manos al pecho; 1 = brazos extendidos)
  'pallof_press': [
    dict(head=(44, 20), neck=(44, 28), shoulder=(44, 32), elbow=(46, 46), hand=(54, 38),
         hip=(44, 60), knee=(45, 72), foot=(47, 86)),
    dict(head=(44, 20), neck=(44, 28), shoulder=(44, 32), elbow=(58, 34), hand=(70, 36),
         hip=(44, 60), knee=(45, 72), foot=(47, 86)),
  ],
  # sentadilla goblet (pesa al pecho)
  'sentadilla_goblet': [
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(50, 40), hand=(52, 40),
         elbow2=(49, 44), hand2=(51, 44),
         hip=(46, 56), knee=(49, 70), foot=(52, 86)),
    dict(head=(42, 36), neck=(42, 44), shoulder=(43, 48), elbow=(48, 56), hand=(50, 54),
         elbow2=(47, 60), hand2=(49, 58),
         hip=(42, 66), knee=(58, 66), foot=(56, 86)),
  ],
  # sentadilla sumo (pies muy abiertos)
  'sentadilla_sumo': [
    dict(head=(48, 18), neck=(48, 26), shoulder=(48, 30), elbow=(52, 44), hand=(54, 58),
         hip=(48, 58), knee=(40, 72), foot=(32, 86), knee2=(56, 72), foot2=(64, 86)),
    dict(head=(48, 34), neck=(48, 42), shoulder=(48, 46), elbow=(52, 58), hand=(54, 68),
         hip=(48, 66), knee=(36, 70), foot=(32, 86), knee2=(60, 70), foot2=(64, 86)),
  ],
  # sentadilla pistol (a una pierna, la otra extendida al frente)
  'sentadilla_pistol': [
    dict(head=(44, 16), neck=(44, 24), shoulder=(44, 28), elbow=(50, 40), hand=(56, 44),
         hip=(44, 56), knee=(46, 70), foot=(46, 86)),
    dict(head=(38, 48), neck=(38, 56), shoulder=(39, 60), elbow=(48, 56), hand=(58, 52),
         hip=(38, 74), knee=(46, 70), foot=(46, 86), knee2=(54, 68), foot2=(70, 68)),
  ],
  # sentadilla bulgara (pie trasero en el banco)
  'sentadilla_bulgara': [
    dict(head=(42, 18), neck=(42, 26), shoulder=(42, 30), elbow=(46, 44), hand=(48, 56),
         hip=(42, 58), knee=(44, 72), foot=(42, 86), knee2=(58, 72), foot2=(70, 66)),
    dict(head=(42, 34), neck=(42, 42), shoulder=(42, 46), elbow=(46, 58), hand=(48, 68),
         hip=(42, 68), knee=(50, 66), foot=(44, 86), knee2=(58, 78), foot2=(70, 66)),
  ],
  # buenos dias (bisagra de cadera, manos tras la cabeza)
  'buenos_dias': [
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(40, 22), hand=(44, 18),
         hip=(46, 56), knee=(47, 70), foot=(48, 86)),
    dict(head=(64, 46), neck=(58, 50), shoulder=(56, 52), elbow=(48, 44), hand=(54, 42),
         hip=(46, 56), knee=(49, 70), foot=(50, 86)),
  ],
  # curl femoral en el suelo (puente; 1 = piernas extendidas)
  'curl_femoral_suelo': [
    dict(head=(18, 78), neck=(24, 80), shoulder=(28, 78), elbow=(24, 84), hand=(20, 86),
         hip=(48, 74), knee=(56, 64), foot=(64, 84)),
    dict(head=(18, 80), neck=(24, 82), shoulder=(28, 82), elbow=(24, 85), hand=(20, 87),
         hip=(48, 82), knee=(64, 80), foot=(82, 86)),
  ],
  # hip thrust (espalda alta en el banco; 0 = cadera abajo; 1 = cadera arriba)
  'hip_thrust': [
    dict(head=(22, 52), neck=(27, 55), shoulder=(32, 56), elbow=(36, 62), hand=(40, 64),
         hip=(50, 72), knee=(62, 70), foot=(64, 86)),
    dict(head=(22, 50), neck=(27, 52), shoulder=(32, 54), elbow=(36, 58), hand=(42, 60),
         hip=(50, 56), knee=(62, 64), foot=(64, 86)),
  ],
  # abduccion de cadera tumbado de lado (0 = piernas juntas; 1 = pierna superior arriba)
  'abduccion_cadera_lateral': [
    dict(head=(16, 74), neck=(22, 79), shoulder=(28, 80), elbow=(24, 74), hand=(18, 70),
         hip=(54, 82), knee=(70, 82), foot=(88, 82), spineMid=(40, 81)),
    dict(head=(16, 74), neck=(22, 79), shoulder=(28, 80), elbow=(24, 74), hand=(18, 70),
         hip=(54, 82), knee=(68, 66), foot=(80, 50), knee2=(70, 82), foot2=(88, 82), spineMid=(40, 81)),
  ],
  # almeja (tumbado de lado, rodillas dobladas; 1 = rodilla superior se abre)
  'almeja': [
    dict(head=(16, 72), neck=(22, 77), shoulder=(28, 78), elbow=(22, 72), hand=(16, 68),
         hip=(50, 80), knee=(64, 74), foot=(54, 84), spineMid=(38, 79)),
    dict(head=(16, 72), neck=(22, 77), shoulder=(28, 78), elbow=(22, 72), hand=(16, 68),
         hip=(50, 80), knee=(54, 60), foot=(54, 84), knee2=(64, 74), foot2=(54, 84), spineMid=(38, 79)),
  ],
  # elevacion de gemelos a una pierna
  'elevacion_gemelos_una_pierna': [
    dict(head=(46, 18), neck=(46, 26), shoulder=(46, 30), elbow=(50, 44), hand=(53, 56),
         hip=(46, 58), knee=(47, 72), foot=(46, 86), knee2=(42, 72), foot2=(38, 64)),
    dict(head=(46, 12), neck=(46, 20), shoulder=(46, 24), elbow=(50, 38), hand=(53, 50),
         hip=(46, 52), knee=(49, 70), foot=(42, 84), knee2=(42, 68), foot2=(38, 60)),
  ],
  # plancha con toque de hombro (1 = una mano toca el hombro contrario)
  'plancha_toque_hombro': [
    dict(head=(25, 40), neck=(29, 46), shoulder=(32, 49), elbow=(34, 67), hand=(35, 86),
         hip=(58, 60), knee=(72, 74), foot=(88, 82), spineMid=(45, 52)),
    dict(head=(25, 40), neck=(29, 46), shoulder=(32, 49), elbow=(26, 62), hand=(33, 47),
         hip=(58, 60), knee=(72, 74), foot=(88, 82), spineMid=(45, 52)),
  ],
  # elevacion de piernas tumbado (0 = piernas en el suelo; 1 = piernas verticales)
  'elevacion_piernas': [
    dict(head=(16, 78), neck=(22, 81), shoulder=(28, 81), elbow=(22, 85), hand=(16, 86),
         hip=(48, 83), knee=(66, 82), foot=(86, 82), spineMid=(38, 82)),
    dict(head=(16, 78), neck=(22, 81), shoulder=(28, 81), elbow=(22, 85), hand=(16, 86),
         hip=(48, 83), knee=(50, 58), foot=(52, 34), spineMid=(38, 82)),
  ],
  # hollow hold (isometrico: dos fotogramas iguales)
  'hollow_hold': [
    dict(head=(18, 70), neck=(24, 76), shoulder=(28, 78), elbow=(16, 66), hand=(6, 60),
         hip=(50, 82), knee=(64, 72), foot=(80, 70), spineMid=(38, 80)),
    dict(head=(18, 70), neck=(24, 76), shoulder=(28, 78), elbow=(16, 66), hand=(6, 60),
         hip=(50, 82), knee=(64, 72), foot=(80, 70), spineMid=(38, 80)),
  ],
  # skipping lateral (desplazamiento lateral; 1 = pierna elevada al lado)
  'skipping_lateral': [
    dict(head=(46, 24), neck=(46, 32), shoulder=(46, 36), elbow=(52, 48), hand=(56, 58),
         hip=(46, 60), knee=(44, 72), foot=(42, 86), knee2=(52, 72), foot2=(54, 86)),
    dict(head=(46, 20), neck=(46, 28), shoulder=(46, 32), elbow=(52, 44), hand=(56, 54),
         hip=(46, 56), knee=(42, 68), foot=(40, 86), knee2=(58, 56), foot2=(62, 70)),
  ],
  # shadow boxing (guardia; 1 = jab directo)
  'shadow_boxing': [
    dict(head=(44, 22), neck=(44, 30), shoulder=(44, 34), elbow=(40, 44), hand=(42, 30),
         elbow2=(50, 44), hand2=(52, 32),
         hip=(46, 62), knee=(44, 74), foot=(42, 86), knee2=(56, 74), foot2=(60, 86)),
    dict(head=(44, 22), neck=(44, 30), shoulder=(44, 34), elbow=(40, 44), hand=(42, 30),
         elbow2=(58, 34), hand2=(72, 32),
         hip=(46, 62), knee=(44, 74), foot=(42, 86), knee2=(56, 74), foot2=(60, 86)),
  ],
  # swing con kettlebell (0 = bisagra, pesa entre piernas; 1 = pesa a la altura del pecho)
  'swing_kettlebell': [
    dict(head=(54, 44), neck=(50, 50), shoulder=(52, 52), elbow=(58, 62), hand=(56, 74),
         hip=(42, 54), knee=(48, 70), foot=(46, 86)),
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(56, 34), hand=(66, 40),
         hip=(46, 56), knee=(47, 70), foot=(48, 86)),
  ],
  # press militar con kettlebell (0 = pesa al hombro; 1 = brazo arriba)
  'press_kettlebell': [
    dict(head=(46, 18), neck=(46, 26), shoulder=(46, 30), elbow=(42, 44), hand=(44, 32),
         hip=(46, 58), knee=(47, 72), foot=(48, 86)),
    dict(head=(46, 18), neck=(46, 26), shoulder=(46, 30), elbow=(47, 16), hand=(47, 6),
         hip=(46, 58), knee=(47, 72), foot=(48, 86)),
  ],
  # paseo del monstruo (banda en las rodillas, media sentadilla, pasos laterales)
  'paseo_monstruo': [
    dict(head=(47, 26), neck=(47, 34), shoulder=(47, 38), elbow=(52, 50), hand=(56, 60),
         hip=(47, 62), knee=(40, 70), foot=(38, 86), knee2=(54, 70), foot2=(56, 86)),
    dict(head=(47, 26), neck=(47, 34), shoulder=(47, 38), elbow=(52, 50), hand=(56, 60),
         hip=(47, 62), knee=(34, 72), foot=(30, 86), knee2=(60, 72), foot2=(64, 86)),
  ],
  # estiramiento de cuadriceps de pie (1 = pie al gluteo sujeto con la mano)
  'estiramiento_cuadriceps': [
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(50, 42), hand=(53, 54),
         hip=(46, 56), knee=(47, 70), foot=(48, 86)),
    dict(head=(46, 16), neck=(46, 24), shoulder=(46, 28), elbow=(42, 40), hand2=(44, 58),
         elbow2=(41, 46), hand=(53, 54),
         hip=(46, 56), knee=(47, 70), foot=(48, 86), knee2=(45, 74), foot2=(44, 58)),
  ],
  # estiramiento de pecho en la pared (0 = brazo apoyado; 1 = giro alejandose)
  'estiramiento_pecho': [
    dict(head=(52, 20), neck=(52, 28), shoulder=(52, 32), elbow=(62, 34), hand=(74, 36),
         hip=(52, 60), knee=(53, 72), foot=(55, 86)),
    dict(head=(46, 20), neck=(46, 28), shoulder=(46, 32), elbow=(58, 34), hand=(74, 36),
         hip=(48, 60), knee=(50, 72), foot=(52, 86)),
  ],
}
PROPS = {
  'almeja': [[], []],
  'elevacion_gemelos_una_pierna': [[], []],
  'plancha_toque_hombro': [[], []],
  'elevacion_piernas': [[], []],
  'hollow_hold': [[], []],
  'skipping_lateral': [[], []],
  'shadow_boxing': [[], []],
  'swing_kettlebell': [[('kb', 56, 78)], [('kb', 70, 42)]],
  'press_kettlebell': [[('kb', 44, 28)], [('kb', 47, 4)]],
  'paseo_monstruo': [[('band', (40, 68), (54, 68))], [('band', (34, 70), (60, 70))]],
  'estiramiento_cuadriceps': [[], []],
  'estiramiento_pecho': [[('wall', 80)], [('wall', 80)]],
  'sentadilla_goblet': [[('kb', 53, 42)], [('kb', 51, 56)]],
  'sentadilla_sumo': [[], []],
  'sentadilla_pistol': [[], []],
  'sentadilla_bulgara': [[('bench', 66, 60, 14, 6)], [('bench', 66, 60, 14, 6)]],
  'buenos_dias': [[], []],
  'curl_femoral_suelo': [[], []],
  'hip_thrust': [[('bench', 16, 52, 12, 8), ('kb', 50, 66)], [('bench', 16, 52, 12, 8), ('kb', 50, 50)]],
  'abduccion_cadera_lateral': [[], []],
  'pajaros': [[('band', (64, 32), (63, 36))], [('band', (70, 42), (20, 42))]],
  'remo_banda': [[('band', (61, 72), (72, 82))], [('band', (38, 74), (72, 82))]],
  'jalon_banda': [[('band', (56, 8), (64, 2))], [('band', (44, 44), (64, 2))]],
  'curl_banda': [[('band', (46, 58), (50, 86))], [('band', (40, 30), (50, 86))]],
  'face_pull_banda': [[('wall', 82), ('band', (82, 30), (62, 32))], [('wall', 82), ('band', (82, 30), (42, 28))]],
  'press_pecho_banda': [[('band', (52, 40), (38, 34))], [('band', (72, 36), (38, 34))]],
  'aperturas_banda': [[('band', (68, 44), (22, 44))], [('band', (64, 36), (40, 30))]],
  'pallof_press': [[('wall', 16), ('band', (16, 38), (54, 38))], [('wall', 16), ('band', (16, 38), (70, 36))]],
  'jumping_jacks': [[], []],
  'rodillas_altas': [[], []],
  'burpees': [[], []],
  'burpees_sin_salto': [[], []],
  'russian_twist': [[], []],
  'dead_bug': [[], []],
  'bird_dog': [[], []],
  'fondos_banco': [[('bench', 20, 60, 16, 6)], [('bench', 20, 60, 16, 6)]],
  'fondos_banco_rodillas': [[('bench', 20, 60, 16, 6)], [('bench', 20, 60, 16, 6)]],
  'pike_push_up': [[], []],
  'zancada': [[], []],
  'zancada_atras': [[], []],
  'subida_banco': [[('bench', 60, 60, 16, 8)], [('bench', 60, 60, 16, 8)]],
  'plancha_rodillas': [[], []],
  'elevacion_gemelos': [[], []],
  'estiramiento_isquios': [[], []],
  'flexiones_rodillas': [[], []],
  'gato_camello': [[], []],
  'sentadilla_silla': [[('chair', 28, 66)], [('chair', 28, 66)]],
}

def render(eid, poses, equip):
    outs = []
    for i, pose in enumerate(poses):
        img = Image.new('RGB', (W, W), 'white')
        dr = ImageDraw.Draw(img)
        draw_ground(dr)
        for e in equip[i]:
            draw_equip(dr, [e])
        draw_fig(dr, pose)
        outs.append(img)
    return outs

if __name__ == '__main__':
    out = pathlib.Path.home() / 'illustrations' / 'out'
    out.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGB', (W * 3, W * 2), 'white')
    for n, (eid, poses) in enumerate(POSES.items()):
        imgs = render(eid, poses, PROPS[eid])
        for i, im in enumerate(imgs):
            sheet.paste(im, (W * n, W * i))
            imgs[i].save(out / f'{eid}_{i}.png')
    sheet.save(out / 'sheet.png')
    print('rendered', list(POSES))
