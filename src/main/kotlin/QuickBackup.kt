/*
 * MIT License
 *
 * Copyright (c) 2021 Leonardo Colman Lopes
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package br.com.colman

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.defaultLazy
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.rockaport.alice.Alice
import com.rockaport.alice.AliceContextBuilder
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate.now
import kotlin.io.path.createTempFile

class QuickBackup : NoOpCliktCommand()

class Restore : CliktCommand() {

    val file by argument().file(mustExist = true)
    val temporary = createTempFile().toFile()

    val password by argument().convert { it.toCharArray() }

    val destinationDir by argument().file().defaultLazy { file.parentFile }

    override fun run() {
        decryptFile()
        unzipFile()
    }

    private fun decryptFile() {
        Alice(AliceContextBuilder().build()).decrypt(file, temporary, password)
    }

    private fun unzipFile() {
        val zipFile = ZipFile(temporary)
        zipFile.entries.asSequence().forEach {
            val destination = File(destinationDir, it.name)
            destinationDir.mkdirs()
            IOUtils.copy(zipFile.getInputStream(it), destination.outputStream())
        }
        file.delete()
    }
}

class Backup : CliktCommand() {

    val filesToZip by argument().path(mustExist = true).multiple()

    val password by argument().convert { it.toCharArray() }

    val temporary = createTempFile().toFile()

    val destination by option().file().default(File("backup-${now()}.zip.enc"))

    override fun run() {
        zipFiles()
        encryptFile()
    }

    private fun zipFiles() {
        ZipArchiveOutputStream(FileOutputStream(temporary)).use { archive ->
            filesToZip.map { it.toFile() }.flatMap { it.walkTopDown().toList() }.filter { it.isFile }.forEach {
                val entry = ZipArchiveEntry(it, it.toString())
                FileInputStream(it).use {
                    archive.putArchiveEntry(entry)
                    IOUtils.copy(it, archive)
                    archive.closeArchiveEntry()
                }
            }
            archive.finish()
        }
    }

    private fun encryptFile() =
        Alice(AliceContextBuilder().build()).encrypt(temporary, destination, password)

}

fun main(args: Array<String>) {
    QuickBackup().subcommands(Backup(), Restore()).main(args)
}
