package com.heartguard.reminder

data class ReminderLaunchEvent(
    val itemName: String,
    val reminderId: Long = -1L,
    val matchedTime: String = "",
    val eventId: Long = System.nanoTime(),
)
