/*
 *
 *  * Copyright (C) 2024 pedroSG94.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.pedro.whip.utils

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Created by pedro on 13/7/25.
 */
object Network {
  fun getNetworks(onlyV4: Boolean): List<InetAddress> {
    val networks = mutableListOf<InetAddress>()
    val networkInterfaces = NetworkInterface.getNetworkInterfaces()
    while (networkInterfaces.hasMoreElements()) {
      val networkInterface = networkInterfaces.nextElement()
      if (!networkInterface.isLoopback && networkInterface.isUp) {
        val addresses = networkInterface.getInetAddresses()
        while (addresses.hasMoreElements()) {
          val address = addresses.nextElement()
          if (!address.isLoopbackAddress) {
            if (address is Inet4Address) networks.add(address)
            if (!onlyV4 && address is Inet6Address && !address.isLinkLocalAddress) networks.add(address)
          }
        }
      }
    }
    return networks
  }
}