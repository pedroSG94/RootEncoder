package com.pedro.srtreceiver

import android.util.Log

class H264Parser {
    
    companion object {
        private const val TAG = "H264Parser"
        
        const val NAL_TYPE_SPS = 7
        const val NAL_TYPE_PPS = 8
        const val NAL_TYPE_IDR = 5
        const val NAL_TYPE_NON_IDR = 1
    }
    
    private val spsData = mutableListOf<ByteArray>()
    private val ppsData = mutableListOf<ByteArray>()
    
    var onNalUnit: ((ByteArray, Int, Long) -> Unit)? = null
    var onConfigReady: ((List<ByteArray>, List<ByteArray>) -> Unit)? = null
    
    fun parse(pesData: ByteArray, pts: Long) {
        val nalUnits = splitNalUnits(pesData)
        
        for (nalUnit in nalUnits) {
            if (nalUnit.isEmpty()) continue
            
            val nalType = nalUnit[0].toInt() and 0x1F
            
            when (nalType) {
                NAL_TYPE_SPS -> {
                    val newSps = nalUnit.copyOf()
                    if (spsData.none { it.contentEquals(newSps) }) {
                        spsData.add(newSps)
                        Log.d(TAG, "SPS received, size=${nalUnit.size}")
                        checkConfigReady()
                    }
                }
                NAL_TYPE_PPS -> {
                    val newPps = nalUnit.copyOf()
                    if (ppsData.none { it.contentEquals(newPps) }) {
                        ppsData.add(newPps)
                        Log.d(TAG, "PPS received, size=${nalUnit.size}")
                        checkConfigReady()
                    }
                }
                NAL_TYPE_IDR, NAL_TYPE_NON_IDR -> {
                    onNalUnit?.invoke(nalUnit, nalType, pts)
                }
                else -> {
                    // Other NAL types
                    onNalUnit?.invoke(nalUnit, nalType, pts)
                }
            }
        }
    }
    
    private fun checkConfigReady() {
        if (spsData.isNotEmpty() && ppsData.isNotEmpty()) {
            onConfigReady?.invoke(spsData, ppsData)
        }
    }
    
    private fun splitNalUnits(data: ByteArray): List<ByteArray> {
        val nalUnits = mutableListOf<ByteArray>()
        var i = 0
        
        while (i < data.size) {
            // Find start code
            val startCodeLen = findStartCode(data, i)
            if (startCodeLen == 0) {
                i++
                continue
            }
            
            // Skip start code
            i += startCodeLen
            val nalStart = i
            
            // Find next start code
            i++
            while (i < data.size) {
                val nextStartCodeLen = findStartCode(data, i)
                if (nextStartCodeLen > 0) {
                    // Found next NAL unit
                    val nalUnit = ByteArray(i - nalStart)
                    System.arraycopy(data, nalStart, nalUnit, 0, nalUnit.size)
                    nalUnits.add(nalUnit)
                    break
                }
                i++
            }
            
            // Handle last NAL unit
            if (i >= data.size && nalStart < data.size) {
                val nalUnit = ByteArray(data.size - nalStart)
                System.arraycopy(data, nalStart, nalUnit, 0, nalUnit.size)
                nalUnits.add(nalUnit)
            }
        }
        
        return nalUnits
    }
    
    private fun findStartCode(data: ByteArray, offset: Int): Int {
        if (offset + 2 >= data.size) return 0
        
        // Check for 00 00 01
        if (data[offset].toInt() == 0x00 && 
            data[offset + 1].toInt() == 0x00 && 
            data[offset + 2].toInt() == 0x01) {
            return 3
        }
        
        // Check for 00 00 00 01
        if (offset + 3 < data.size &&
            data[offset].toInt() == 0x00 && 
            data[offset + 1].toInt() == 0x00 && 
            data[offset + 2].toInt() == 0x00 &&
            data[offset + 3].toInt() == 0x01) {
            return 4
        }
        
        return 0
    }
    
    fun hasSpsAndPps(): Boolean {
        return spsData.isNotEmpty() && ppsData.isNotEmpty()
    }
    
    fun getSps(): List<ByteArray> = spsData
    fun getPps(): List<ByteArray> = ppsData
}
