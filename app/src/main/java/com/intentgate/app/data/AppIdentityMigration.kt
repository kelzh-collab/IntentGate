package com.intentgate.app.data

fun migrateSessionAppIds(book:IntentSessionBook)=book.copy(
    requests=book.requests.map { it.copy(appId=it.packageName) },
    sessions=book.sessions.map { it.copy(appId=it.packageName) })
fun migrateLimitAppIds(ledger:DailyLimitLedger)=ledger.copy(
    extensions=ledger.extensions.map { it.copy(appId=AppIdentity.packageId(it.appId)) },
    satisfied=ledger.satisfied.map { it.copy(appId=AppIdentity.packageId(it.appId)) }.toSet())
