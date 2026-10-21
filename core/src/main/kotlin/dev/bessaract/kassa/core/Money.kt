package dev.bessaract.kassa.core

import kotlin.math.roundToLong

/** Перевод суммы в копейках валюты в копейки рубля по курсу (рублей за единицу). */
fun toRub(amt: Long, rate: Double): Long = (amt * rate).roundToLong()
