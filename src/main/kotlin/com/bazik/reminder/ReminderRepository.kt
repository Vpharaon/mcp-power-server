package com.bazik.reminder

import com.bazik.reminder.models.*
import com.bazik.time.TimeService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Reminders : Table("reminders") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val description = text("description")
    val dueDate = varchar("due_date", 50).nullable()
    val priority = varchar("priority", 20)
    val status = varchar("status", 20)
    val createdAt = varchar("created_at", 50)
    val updatedAt = varchar("updated_at", 50)
    // Agent task fields
    val agentTask = text("agent_task").nullable()
    val executeAt = varchar("execute_at", 50).nullable()
    val city = varchar("city", 255).nullable()
    val lastExecutedAt = varchar("last_executed_at", 50).nullable()
    val executionResult = text("execution_result").nullable()

    override val primaryKey = PrimaryKey(id)
}

object NotificationSchedules : Table("notification_schedules") {
    val id = long("id").autoIncrement()
    val intervalMinutes = integer("interval_minutes")
    val isEnabled = bool("is_enabled")
    val lastSentAt = varchar("last_sent_at", 50).nullable()
    val createdAt = varchar("created_at", 50)
    val updatedAt = varchar("updated_at", 50)

    override val primaryKey = PrimaryKey(id)
}

class ReminderRepository(
    private val database: Database,
    private val defaultZoneId: ZoneId = ZoneId.of("Europe/Moscow")
) {

    init {
        transaction(database) {
            SchemaUtils.create(Reminders, NotificationSchedules)
        }
    }

    fun addReminder(
        title: String,
        description: String,
        dueDate: String? = null,
        priority: ReminderPriority = ReminderPriority.MEDIUM,
        agentTask: String? = null,
        executeAt: String? = null,
        city: String? = null
    ): Reminder = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val id = Reminders.insert {
            it[Reminders.title] = title
            it[Reminders.description] = description
            it[Reminders.dueDate] = dueDate
            it[Reminders.priority] = priority.name
            it[Reminders.status] = ReminderStatus.ACTIVE.name
            it[Reminders.createdAt] = now
            it[Reminders.updatedAt] = now
            it[Reminders.agentTask] = agentTask
            it[Reminders.executeAt] = executeAt
            it[Reminders.city] = city
            it[Reminders.lastExecutedAt] = null
            it[Reminders.executionResult] = null
        } get Reminders.id

        Reminder(
            id = id,
            title = title,
            description = description,
            dueDate = dueDate,
            priority = priority,
            status = ReminderStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            agentTask = agentTask,
            executeAt = executeAt,
            city = city,
            lastExecutedAt = null,
            executionResult = null
        )
    }

    fun getAllReminders(): List<Reminder> = transaction(database) {
        Reminders.selectAll().map { rowToReminder(it) }
    }

    fun getReminderById(id: Long): Reminder? = transaction(database) {
        Reminders.select { Reminders.id eq id }
            .map { rowToReminder(it) }
            .singleOrNull()
    }

    fun getRemindersByStatus(status: ReminderStatus): List<Reminder> = transaction(database) {
        Reminders.select { Reminders.status eq status.name }
            .map { rowToReminder(it) }
    }

    fun updateReminderStatus(id: Long, status: ReminderStatus): Boolean = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        Reminders.update({ Reminders.id eq id }) {
            it[Reminders.status] = status.name
            it[updatedAt] = now
        } > 0
    }

    fun deleteReminder(id: Long): Boolean = transaction(database) {
        Reminders.deleteWhere { Reminders.id eq id } > 0
    }

    fun getOverdueReminders(): List<Reminder> = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime()

        Reminders.select {
            (Reminders.status eq ReminderStatus.ACTIVE.name) and
            (Reminders.dueDate.isNotNull())
        }.mapNotNull { row ->
            val reminder = rowToReminder(row)
            val dueDate = reminder.dueDate?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
            if (dueDate != null && dueDate.isBefore(now)) {
                reminder
            } else {
                null
            }
        }
    }

    fun getUpcomingReminders(hours: Int = 24): List<Reminder> = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime()
        val future = now.plusHours(hours.toLong())

        Reminders.select {
            (Reminders.status eq ReminderStatus.ACTIVE.name) and
            (Reminders.dueDate.isNotNull())
        }.mapNotNull { row ->
            val reminder = rowToReminder(row)
            val dueDate = reminder.dueDate?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
            if (dueDate != null && dueDate.isAfter(now) && dueDate.isBefore(future)) {
                reminder
            } else {
                null
            }
        }.sortedBy { it.dueDate }
    }

    fun getHighPriorityReminders(): List<Reminder> = transaction(database) {
        Reminders.select {
            (Reminders.status eq ReminderStatus.ACTIVE.name) and
            ((Reminders.priority eq ReminderPriority.HIGH.name) or
             (Reminders.priority eq ReminderPriority.URGENT.name))
        }.map { rowToReminder(it) }
    }

    // Notification Schedule methods

    fun setNotificationSchedule(intervalMinutes: Int, isEnabled: Boolean = true): NotificationSchedule = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        // Delete existing schedule (we'll keep only one)
        NotificationSchedules.deleteAll()

        val id = NotificationSchedules.insert {
            it[NotificationSchedules.intervalMinutes] = intervalMinutes
            it[NotificationSchedules.isEnabled] = isEnabled
            it[NotificationSchedules.lastSentAt] = null
            it[NotificationSchedules.createdAt] = now
            it[NotificationSchedules.updatedAt] = now
        } get NotificationSchedules.id

        NotificationSchedule(
            id = id,
            intervalMinutes = intervalMinutes,
            isEnabled = isEnabled,
            lastSentAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun getNotificationSchedule(): NotificationSchedule? = transaction(database) {
        NotificationSchedules.selectAll()
            .map { rowToNotificationSchedule(it) }
            .singleOrNull()
    }

    fun updateLastSentAt(scheduleId: Long): Boolean = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        NotificationSchedules.update({ NotificationSchedules.id eq scheduleId }) {
            it[lastSentAt] = now
            it[updatedAt] = now
        } > 0
    }

    fun updateScheduleEnabled(scheduleId: Long, isEnabled: Boolean): Boolean = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        NotificationSchedules.update({ NotificationSchedules.id eq scheduleId }) {
            it[NotificationSchedules.isEnabled] = isEnabled
            it[updatedAt] = now
        } > 0
    }

    suspend fun getPendingAgentTasks(timeService: TimeService): List<Reminder> = transaction(database) {
        Reminders.select {
            (Reminders.status eq ReminderStatus.ACTIVE.name) and
            (Reminders.agentTask.isNotNull()) and
            (Reminders.executeAt.isNotNull())
        }.mapNotNull { row ->
            val reminder = rowToReminder(row)

            // Пропускаем задачи, которые уже были выполнены
            if (reminder.lastExecutedAt != null) {
                return@mapNotNull null
            }

            val executeAt = reminder.executeAt?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } ?: return@mapNotNull null

            // Получаем текущее время в городе пользователя
            val currentTime = if (reminder.city != null) {
                // Используем TimeService для получения времени в указанном городе
                val cityTimeResult = kotlinx.coroutines.runBlocking {
                    timeService.getCityTime(reminder.city)
                }

                if (cityTimeResult.isSuccess) {
                    val cityTimeInfo = cityTimeResult.getOrThrow()
                    // Парсим currentDate и currentTime в LocalDateTime
                    LocalDateTime.parse("${cityTimeInfo.currentDate}T${cityTimeInfo.currentTime}")
                } else {
                    // Fallback на настроенный часовой пояс, если не удалось получить время города
                    ZonedDateTime.now(defaultZoneId).toLocalDateTime()
                }
            } else {
                // Если город не указан, используем настроенный часовой пояс
                ZonedDateTime.now(defaultZoneId).toLocalDateTime()
            }

            // Возвращаем задачу, если время выполнения наступило
            if (!executeAt.isAfter(currentTime)) {
                reminder
            } else {
                null
            }
        }
    }

    fun updateExecutionResult(id: Long, result: String): Boolean = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        Reminders.update({ Reminders.id eq id }) {
            it[lastExecutedAt] = now
            it[executionResult] = result
            it[updatedAt] = now
        } > 0
    }

    private fun rowToReminder(row: ResultRow): Reminder = Reminder(
        id = row[Reminders.id],
        title = row[Reminders.title],
        description = row[Reminders.description],
        dueDate = row[Reminders.dueDate],
        priority = ReminderPriority.valueOf(row[Reminders.priority]),
        status = ReminderStatus.valueOf(row[Reminders.status]),
        createdAt = row[Reminders.createdAt],
        updatedAt = row[Reminders.updatedAt],
        agentTask = row[Reminders.agentTask],
        executeAt = row[Reminders.executeAt],
        city = row[Reminders.city],
        lastExecutedAt = row[Reminders.lastExecutedAt],
        executionResult = row[Reminders.executionResult]
    )

    private fun rowToNotificationSchedule(row: ResultRow): NotificationSchedule = NotificationSchedule(
        id = row[NotificationSchedules.id],
        intervalMinutes = row[NotificationSchedules.intervalMinutes],
        isEnabled = row[NotificationSchedules.isEnabled],
        lastSentAt = row[NotificationSchedules.lastSentAt],
        createdAt = row[NotificationSchedules.createdAt],
        updatedAt = row[NotificationSchedules.updatedAt]
    )
}