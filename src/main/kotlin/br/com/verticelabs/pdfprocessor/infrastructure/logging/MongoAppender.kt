package br.com.verticelabs.pdfprocessor.infrastructure.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import org.bson.Document
import java.util.Date
import java.util.concurrent.TimeUnit

class MongoAppender : AppenderBase<ILoggingEvent>() {

    var uri: String = ""
    var collection: String = "logs"
    var retentionDays: Long = 30
    var enabled: Boolean = true

    private var mongoClient: MongoClient? = null
    private var logCollection: MongoCollection<Document>? = null

    override fun start() {
        if (!enabled) {
            return
        }
        
        if (uri.isBlank()) {
            addError("MongoDB URI is missing")
            return
        }

        try {
            val connectionString = ConnectionString(uri)
            mongoClient = MongoClients.create(connectionString)
            
            val dbName = connectionString.database ?: "pdfprocessor"
            val db = mongoClient!!.getDatabase(dbName)
            logCollection = db.getCollection(collection)

            // Ensure TTL index exists
            try {
                val indexOptions = IndexOptions().expireAfter(retentionDays, TimeUnit.DAYS)
                logCollection!!.createIndex(Indexes.ascending("timestamp"), indexOptions)
            } catch (e: Exception) {
                addError("Failed to create TTL index", e)
            }

            super.start()
        } catch (e: Exception) {
            addError("Failed to start MongoAppender: ${e.message}", e)
        }
    }

    override fun stop() {
        mongoClient?.close()
        super.stop()
    }

    override fun append(event: ILoggingEvent) {
        if (logCollection == null) return

        try {
            val doc = Document()
                .append("timestamp", Date(event.timeStamp))
                .append("level", event.level.toString())
                .append("logger", event.loggerName)
                .append("thread", event.threadName)
                .append("message", event.formattedMessage)

            if (event.throwableProxy != null) {
                doc.append("exception", event.throwableProxy.className + ": " + event.throwableProxy.message)
            }
            
            if (event.mdcPropertyMap != null && event.mdcPropertyMap.isNotEmpty()) {
                doc.append("context", event.mdcPropertyMap)
            }

            logCollection!!.insertOne(doc)
        } catch (e: Exception) {
            addError("Failed to write log to MongoDB", e)
        }
    }
}
