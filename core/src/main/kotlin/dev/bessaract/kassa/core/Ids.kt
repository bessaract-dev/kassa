package dev.bessaract.kassa.core

import java.util.UUID

/** Новый уникальный id записи. */
fun newId(): String = UUID.randomUUID().toString()
