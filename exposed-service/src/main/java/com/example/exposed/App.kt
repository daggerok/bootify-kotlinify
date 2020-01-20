package com.example.exposed

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.support.beans
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.servlet.function.router
import java.time.LocalDateTime
import java.util.*

@SpringBootApplication
class App

fun main(args: Array<String>) {
  SpringApplicationBuilder()
      .sources(App::class.java)
      .initializers(beans {
        bean {
          // test-data
          val messageService = ref<MessageService>()
          val body = "ololo trololo"
          listOf("ololo", "trololo")
              .map { Message(it, body) }
              .forEach { messageService.save(it) }
        }
        bean {
          // print stored test-data...
          val messageService = ref<MessageService>()
          messageService.findAll().forEach { println(it) }
        }
        bean {
          val messageService = ref<MessageService>()
          router {
            "/".nest {
              POST("/**") {
                val message = it.body(Message::class.java)
                messageService.save(message)
                accepted().build()
              }
              GET("/{id}") {
                val id = it.pathVariable("id").toLong()
                val maybeMessage = messageService.finById(id)
                if (maybeMessage.isPresent) ok().body(maybeMessage.get())
                else notFound().build()
              }
              GET("/**") {
                ok().body(messageService.findAll())
              }
            }
          }
        }
      })
      .run(*args)
}

@Service
@Transactional
class MessageRepository(private val transactionTemplate: TransactionTemplate) : MessageService, InitializingBean {

  override fun findAll(): Collection<Message> = Messages.selectAll()
      // .map { Message(it[Messages.author], it[Messages.body], it[Messages.at], it[Messages.id]) }
      .toMessage()

  override fun finById(id: Long): Optional<Message> = Optional
      .ofNullable(
          Messages
              .select { Messages.id.eq(id) }
              // .map { Message(it[Messages.author], it[Messages.body], it[Messages.at], it[Messages.id]) }
              .toMessage()
              .firstOrNull())

  override fun save(message: Message) {
    Messages.insert {
      it[Messages.author] = message.author
      it[Messages.body] = message.body
      it[Messages.at] = message.at
    }
  }

  override fun afterPropertiesSet() {
    transactionTemplate.execute {
      SchemaUtils.create(Messages)
    }
  }
}

fun Query.toMessage() = this
    .map { Message(it[Messages.author], it[Messages.body], it[Messages.at], it[Messages.id]) }

interface MessageService {
  fun findAll(): Collection<Message>
  fun finById(id: Long): Optional<Message>
  fun save(message: Message)
}

object Messages : Table() {
  var author = varchar("author", 255)
  var body = text("body")
  var at = datetime("at")
  val id = long("id").autoIncrement()
  override val primaryKey = PrimaryKey(id, name = "MessagesPK")
}

data class Message(val author: String,
                   val body: String,
                   val at: LocalDateTime = LocalDateTime.now(),
                   val id: Long? = null)
