package com.bazik.reminder

import com.bazik.reminder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Таблица Tasks (переименовано из Reminders)
 * Содержит поля согласно новой модели Task
 */
object Tasks : Table("tasks") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val description = text("description")
    val reminderDateTime = varchar("reminder_date_time", 50) // ISO format время сервера
    val recurrence = varchar("recurrence", 20).nullable() // DAILY, WEEKLY, MONTHLY
    val importance = varchar("importance", 20) // LOW, MEDIUM, HIGH, URGENT
    val isCompleted = bool("is_completed").default(false)
    val createdAt = varchar("created_at", 50)
    val updatedAt = varchar("updated_at", 50)

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

/**
 * TaskRepository (переименовано из ReminderRepository)
 * Управляет задачами в базе данных SQLite
 */
class TaskRepository(
    private val database: Database,
    private val defaultZoneId: ZoneId = ZoneId.of("Europe/Moscow")
) {

    init {
        transaction(database) {
            SchemaUtils.create(Tasks, NotificationSchedules)
        }
    }

    /**
     * Добавить задачу
     * Параметр title может быть null - в этом случае он будет сгенерирован из description
     */
    fun addTask(
        title: String?,
        description: String,
        reminderDateTime: String, // ISO format
        recurrence: TaskRecurrence? = null,
        importance: TaskImportance = TaskImportance.MEDIUM
    ): Task = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        // Генерация title из description, если title не указан
        val finalTitle = if (title.isNullOrBlank()) {
            generateTitleFromDescription(description)
        } else {
            title
        }

        val id = Tasks.insert {
            it[Tasks.title] = finalTitle
            it[Tasks.description] = description
            it[Tasks.reminderDateTime] = reminderDateTime
            it[Tasks.recurrence] = recurrence?.name
            it[Tasks.importance] = importance.name
            it[Tasks.isCompleted] = false
            it[Tasks.createdAt] = now
            it[Tasks.updatedAt] = now
        } get Tasks.id

        Task(
            id = id,
            title = finalTitle,
            description = description,
            reminderDateTime = reminderDateTime,
            recurrence = recurrence,
            importance = importance,
            isCompleted = false,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Получить все задачи
     */
    fun getAllTasks(): List<Task> = transaction(database) {
        Tasks.selectAll().map { rowToTask(it) }
    }

    /**
     * Получить задачу по ID
     */
    fun getTaskById(id: Long): Task? = transaction(database) {
        Tasks.select { Tasks.id eq id }
            .map { rowToTask(it) }
            .singleOrNull()
    }

    /**
     * Получить задачи по статусу (активные/завершенные)
     */
    fun getTasksByCompleted(isCompleted: Boolean): List<Task> = transaction(database) {
        Tasks.select { Tasks.isCompleted eq isCompleted }
            .map { rowToTask(it) }
    }

    /**
     * Получить задачи по важности
     */
    fun getTasksByImportance(importance: TaskImportance): List<Task> = transaction(database) {
        Tasks.select { Tasks.importance eq importance.name }
            .map { rowToTask(it) }
    }

    /**
     * Получить задачи на конкретную дату
     */
    fun getTasksByDate(date: LocalDate): List<Task> = transaction(database) {
        Tasks.selectAll().mapNotNull { row ->
            val task = rowToTask(row)
            val taskDateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            if (taskDateTime.toLocalDate() == date) {
                task
            } else {
                null
            }
        }
    }

    /**
     * Получить задачи, которые нужно выполнить (время наступило)
     * Возвращает задачи со статусом isCompleted = false и reminderDateTime <= now
     */
    fun getTasksToProcess(now: LocalDateTime): List<Task> = transaction(database) {
        Tasks.select { Tasks.isCompleted eq false }
            .mapNotNull { row ->
                val task = rowToTask(row)
                val taskDateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                if (!taskDateTime.isAfter(now)) {
                    task
                } else {
                    null
                }
            }
    }

    /**
     * Пометить задачу как обработанную
     * Если есть recurrence, рассчитывается следующая дата
     * Если нет recurrence, задача помечается как завершенная
     */
    fun markTaskAsProcessed(task: Task): Boolean = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        if (task.recurrence == null) {
            // Нет повторения - помечаем как завершенную
            Tasks.update({ Tasks.id eq task.id }) {
                it[isCompleted] = true
                it[updatedAt] = now
            } > 0
        } else {
            // Есть повторение - рассчитываем следующую дату
            val currentDateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val nextDateTime = calculateNextDateTime(currentDateTime, task.recurrence)

            Tasks.update({ Tasks.id eq task.id }) {
                it[reminderDateTime] = nextDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                it[updatedAt] = now
            } > 0
        }
    }

    /**
     * Обновить статус задачи (завершена/активна)
     */
    fun updateTaskCompletion(id: Long, isCompleted: Boolean): Boolean = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        Tasks.update({ Tasks.id eq id }) {
            it[Tasks.isCompleted] = isCompleted
            it[updatedAt] = now
        } > 0
    }

    /**
     * Удалить задачу
     */
    fun deleteTask(id: Long): Boolean = transaction(database) {
        Tasks.deleteWhere { Tasks.id eq id } > 0
    }

    /**
     * Получить просроченные задачи
     */
    fun getOverdueTasks(): List<Task> = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime()

        Tasks.select { Tasks.isCompleted eq false }
            .mapNotNull { row ->
                val task = rowToTask(row)
                val taskDateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                if (taskDateTime.isBefore(now)) {
                    task
                } else {
                    null
                }
            }
    }

    /**
     * Получить предстоящие задачи (в ближайшие N часов)
     */
    fun getUpcomingTasks(hours: Int = 24): List<Task> = transaction(database) {
        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime()
        val future = now.plusHours(hours.toLong())

        Tasks.select { Tasks.isCompleted eq false }
            .mapNotNull { row ->
                val task = rowToTask(row)
                val taskDateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                if (taskDateTime.isAfter(now) && taskDateTime.isBefore(future)) {
                    task
                } else {
                    null
                }
            }.sortedBy { it.reminderDateTime }
    }

    /**
     * Получить задачи высокой важности
     */
    fun getHighPriorityTasks(): List<Task> = transaction(database) {
        Tasks.select {
            (Tasks.isCompleted eq false) and
            ((Tasks.importance eq TaskImportance.HIGH.name) or
             (Tasks.importance eq TaskImportance.URGENT.name))
        }.map { rowToTask(it) }
    }

    // ============= Notification Schedule methods =============

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

    // ============= Helper methods =============

    /**
     * Генерация заголовка из описания (первые 5-7 слов)
     */
    private fun generateTitleFromDescription(description: String): String {
        val words = description.trim().split(Regex("\\s+"))
        val titleWords = words.take(7)
        val title = titleWords.joinToString(" ")
        return if (title.length > 50) {
            title.substring(0, 47) + "..."
        } else {
            title
        }
    }

    /**
     * Рассчитать следующую дату/время выполнения на основе типа повторения
     */
    private fun calculateNextDateTime(current: LocalDateTime, recurrence: TaskRecurrence): LocalDateTime {
        return when (recurrence) {
            TaskRecurrence.DAILY -> current.plusDays(1)
            TaskRecurrence.WEEKLY -> current.plusWeeks(1)
            TaskRecurrence.MONTHLY -> current.plusMonths(1)
        }
    }

    private fun rowToTask(row: ResultRow): Task = Task(
        id = row[Tasks.id],
        title = row[Tasks.title],
        description = row[Tasks.description],
        reminderDateTime = row[Tasks.reminderDateTime],
        recurrence = row[Tasks.recurrence]?.let { TaskRecurrence.valueOf(it) },
        importance = TaskImportance.valueOf(row[Tasks.importance]),
        isCompleted = row[Tasks.isCompleted],
        createdAt = row[Tasks.createdAt],
        updatedAt = row[Tasks.updatedAt]
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
