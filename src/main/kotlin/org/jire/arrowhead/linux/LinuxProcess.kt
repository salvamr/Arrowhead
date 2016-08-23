/*
 * Copyright 2016 Thomas Nappo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jire.arrowhead.linux

import com.sun.jna.Pointer
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import org.jire.arrowhead.Module
import org.jire.arrowhead.Process
import java.nio.file.Files
import java.nio.file.Paths
import java.lang.Long.parseLong

class LinuxProcess(override val id: Int) : Process {

	private val local = ThreadLocal.withInitial { iovec() }
	private val remote = ThreadLocal.withInitial { iovec() }

	override val modules: Map<String, Module> by lazy {
		val map = Object2ObjectArrayMap<String, Module>()

		for (line in Files.readAllLines(Paths.get("/proc/$id/maps"))) {
			val split = line.split(" ")
			val regionSplit = split[0].split("-")

			val start = parseLong(regionSplit[0], 16)
			val end = parseLong(regionSplit[1], 16)

			val offset = parseLong(split[2], 16)
			if (offset <= 0) continue

			var path = "";
			var i = 5
			while (i < split.size) {
				val s = split[i].trim { it <= ' ' }
				if (s.isEmpty() && ++i > split.size) break
				else if (s.isEmpty() && !split[i].trim { it <= ' ' }.isEmpty()) path += split[i]
				else if (!s.isEmpty()) path += split[i]
				i++
			}

			val moduleName = path.substring(path.lastIndexOf("/") + 1, path.length)
			map.put(moduleName, LinuxModule(start, this, moduleName, end - start))
		}

		return@lazy map
	}

	override fun read(address: Pointer, data: Pointer, bytesToRead: Int) {
		val local = local.get()
		local.iov_base = data
		local.iov_len = bytesToRead

		val remote = remote.get()
		remote.iov_base = address
		remote.iov_len = bytesToRead

		if (uio.process_vm_readv(id, local, 1, remote, 1, 0) != bytesToRead.toLong())
			throw IllegalStateException("Failed to read $bytesToRead bytes at address $address")
	}

	override fun write(address: Pointer, data: Pointer, bytesToWrite: Int) {
		val local = local.get()
		local.iov_base = data
		local.iov_len = bytesToWrite

		val remote = remote.get()
		remote.iov_base = address
		remote.iov_len = bytesToWrite

		if (uio.process_vm_writev(id, local, 1, remote, 1, 0) != bytesToWrite.toLong())
			throw IllegalStateException("Failed to write $bytesToWrite bytes at address $address")
	}

}