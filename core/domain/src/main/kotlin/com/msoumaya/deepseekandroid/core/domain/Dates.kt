package com.msoumaya.deepseekandroid.core.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Arithmétique de dates calendaires locales.
 *
 * Porté depuis `src/core/program.ts`. Deux règles sont structurantes et doivent être
 * respectées à l'identique :
 *
 *  1. Une date est une chaîne `AAAA-MM-JJ` en **calendrier local**, jamais un instant.
 *     Les comparaisons se font donc par comparaison de chaînes, ce qui donne bien
 *     « du lundi 00:00 au dimanche 23:59 » en heure locale, changement d'heure inclus.
 *  2. [addDays] ancre le calcul à midi (`T12:00:00`) : à minuit, un décalage d'une heure
 *     lors d'un changement d'heure ferait basculer la date d'un jour.
 */
object Dates {

    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dateKey(date: LocalDate): String = date.format(KEY)

    fun todayLocal(): String = dateKey(LocalDate.now())

    fun parse(key: String): LocalDate = LocalDate.parse(key, KEY)

    /** Décale une date de [days] jours. Ancrée à midi pour ignorer les changements d'heure. */
    fun addDays(key: String, days: Int): String = dateKey(parse(key).plusDays(days.toLong()))

    /** Jour de la semaine, 0 = dimanche … 6 = samedi. Convention identique à JavaScript. */
    fun dayOf(key: String): Int = parse(key).dayOfWeek.value % 7

    /**
     * Lundi de la semaine de [key], au format `AAAA-MM-JJ`.
     *
     * La semaine commence le **lundi** — c'est celle de `weeklyProgress` dans le client
     * d'origine, celle du graphique de l'écran « Progrès », et celle de l'objectif partagé
     * entre deux amis. La règle est écrite **une seule fois** ici : un décalage d'un jour ne se
     * verrait que sur l'un des trois écrans, et rien ne le signalerait.
     *
     * Un dimanche appartient donc à la semaine du lundi **précédent**, ce que le `% 7` de
     * [dayOf] exprime sans cas particulier : pour un dimanche (`dayOf` = 0) le recul vaut 6.
     */
    fun weekStart(key: String = todayLocal()): String = addDays(key, -((dayOf(key) + 6) % 7))

    /** Nombre de jours entiers entre deux dates. */
    fun age(from: String, to: String): Int =
        ChronoUnit.DAYS.between(parse(from), parse(to)).toInt()

    /** Instant courant au format ISO 8601 complet, comme `new Date().toISOString()`. */
    fun nowIso(): String = java.time.Instant.now().toString()

    /**
     * Décalage ISO 8601 d'un instant, utilisé pour `updatedAt`.
     * Le format produit est identique à celui de JavaScript : millisecondes et `Z`.
     */
    fun iso(millis: Long): String = java.time.Instant.ofEpochMilli(millis).toString()

    fun parseIsoMillis(iso: String): Long = runCatching {
        java.time.Instant.parse(iso).toEpochMilli()
    }.getOrElse { 0L }

    /** Fuseau local, exposé pour les notifications et l'heure de la question du jour. */
    fun zone(): ZoneId = ZoneId.systemDefault()
}
