package com.cuistre.codexdone

import java.net.Inet4Address
import java.net.NetworkInterface

object NetInfo {
    /** 取本机局域网 IPv4（优先 wlan 网卡），没有就返回 null。 */
    fun lanIp(): String? {
        return try {
            val found = ArrayList<Pair<String, String>>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        found.add(nif.name to host)
                    }
                }
            }
            found.firstOrNull { it.first.startsWith("wlan") }?.second ?: found.firstOrNull()?.second
        } catch (e: Exception) {
            null
        }
    }
}
