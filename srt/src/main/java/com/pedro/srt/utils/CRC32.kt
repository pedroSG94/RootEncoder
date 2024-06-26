/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.srt.utils

/**
 * Created by pedro on 28/8/23.
 */
object CRC32 {
    private val crcTable = intArrayOf(
        0x00000000, 0x04c11db7, 0x09823b6e, 0x0d4326d9,
        0x130476dc, 0x17c56b6b, 0x1a864db2, 0x1e475005,
        0x2608edb8, 0x22c9f00f, 0x2f8ad6d6, 0x2b4bcb61,
        0x350c9b64, 0x31cd86d3, 0x3c8ea00a, 0x384fbdbd,
        0x4c11db70, 0x48d0c6c7, 0x4593e01e, 0x4152fda9,
        0x5f15adac, 0x5bd4b01b, 0x569796c2, 0x52568b75,
        0x6a1936c8, 0x6ed82b7f, 0x639b0da6, 0x675a1011,
        0x791d4014, 0x7ddc5da3, 0x709f7b7a, 0x745e66cd,
        0x9823b6e0.toInt(), 0x9ce2ab57.toInt(), 0x91a18d8e.toInt(), 0x95609039.toInt(),
        0x8b27c03c.toInt(), 0x8fe6dd8b.toInt(), 0x82a5fb52.toInt(), 0x8664e6e5.toInt(),
        0xbe2b5b58.toInt(), 0xbaea46ef.toInt(), 0xb7a96036.toInt(), 0xb3687d81.toInt(),
        0xad2f2d84.toInt(), 0xa9ee3033.toInt(), 0xa4ad16ea.toInt(), 0xa06c0b5d.toInt(),
        0xd4326d90.toInt(), 0xd0f37027.toInt(), 0xddb056fe.toInt(), 0xd9714b49.toInt(),
        0xc7361b4c.toInt(), 0xc3f706fb.toInt(), 0xceb42022.toInt(), 0xca753d95.toInt(),
        0xf23a8028.toInt(), 0xf6fb9d9f.toInt(), 0xfbb8bb46.toInt(), 0xff79a6f1.toInt(),
        0xe13ef6f4.toInt(), 0xe5ffeb43.toInt(), 0xe8bccd9a.toInt(), 0xec7dd02d.toInt(),
        0x34867077, 0x30476dc0, 0x3d044b19, 0x39c556ae,
        0x278206ab, 0x23431b1c, 0x2e003dc5, 0x2ac12072,
        0x128e9dcf, 0x164f8078, 0x1b0ca6a1, 0x1fcdbb16,
        0x018aeb13, 0x054bf6a4, 0x0808d07d, 0x0cc9cdca,
        0x7897ab07, 0x7c56b6b0, 0x71159069, 0x75d48dde,
        0x6b93dddb, 0x6f52c06c, 0x6211e6b5, 0x66d0fb02,
        0x5e9f46bf, 0x5a5e5b08, 0x571d7dd1, 0x53dc6066,
        0x4d9b3063, 0x495a2dd4, 0x44190b0d, 0x40d816ba,
        0xaca5c697.toInt(), 0xa864db20.toInt(), 0xa527fdf9.toInt(), 0xa1e6e04e.toInt(),
        0xbfa1b04b.toInt(), 0xbb60adfc.toInt(), 0xb6238b25.toInt(), 0xb2e29692.toInt(),
        0x8aad2b2f.toInt(), 0x8e6c3698.toInt(), 0x832f1041.toInt(), 0x87ee0df6.toInt(),
        0x99a95df3.toInt(), 0x9d684044.toInt(), 0x902b669d.toInt(), 0x94ea7b2a.toInt(),
        0xe0b41de7.toInt(), 0xe4750050.toInt(), 0xe9362689.toInt(), 0xedf73b3e.toInt(),
        0xf3b06b3b.toInt(), 0xf771768c.toInt(), 0xfa325055.toInt(), 0xfef34de2.toInt(),
        0xc6bcf05f.toInt(), 0xc27dede8.toInt(), 0xcf3ecb31.toInt(), 0xcbffd686.toInt(),
        0xd5b88683.toInt(), 0xd1799b34.toInt(), 0xdc3abded.toInt(), 0xd8fba05a.toInt(),
        0x690ce0ee, 0x6dcdfd59, 0x608edb80, 0x644fc637,
        0x7a089632, 0x7ec98b85, 0x738aad5c, 0x774bb0eb,
        0x4f040d56, 0x4bc510e1, 0x46863638, 0x42472b8f,
        0x5c007b8a, 0x58c1663d, 0x558240e4, 0x51435d53,
        0x251d3b9e, 0x21dc2629, 0x2c9f00f0, 0x285e1d47,
        0x36194d42, 0x32d850f5, 0x3f9b762c, 0x3b5a6b9b,
        0x0315d626, 0x07d4cb91, 0x0a97ed48, 0x0e56f0ff,
        0x1011a0fa, 0x14d0bd4d, 0x19939b94, 0x1d528623,
        0xf12f560e.toInt(), 0xf5ee4bb9.toInt(), 0xf8ad6d60.toInt(), 0xfc6c70d7.toInt(),
        0xe22b20d2.toInt(), 0xe6ea3d65.toInt(), 0xeba91bbc.toInt(), 0xef68060b.toInt(),
        0xd727bbb6.toInt(), 0xd3e6a601.toInt(), 0xdea580d8.toInt(), 0xda649d6f.toInt(),
        0xc423cd6a.toInt(), 0xc0e2d0dd.toInt(), 0xcda1f604.toInt(), 0xc960ebb3.toInt(),
        0xbd3e8d7e.toInt(), 0xb9ff90c9.toInt(), 0xb4bcb610.toInt(), 0xb07daba7.toInt(),
        0xae3afba2.toInt(), 0xaafbe615.toInt(), 0xa7b8c0cc.toInt(), 0xa379dd7b.toInt(),
        0x9b3660c6.toInt(), 0x9ff77d71.toInt(), 0x92b45ba8.toInt(), 0x9675461f.toInt(),
        0x8832161a.toInt(), 0x8cf30bad.toInt(), 0x81b02d74.toInt(), 0x857130c3.toInt(),
        0x5d8a9099, 0x594b8d2e, 0x5408abf7, 0x50c9b640,
        0x4e8ee645, 0x4a4ffbf2, 0x470cdd2b, 0x43cdc09c,
        0x7b827d21, 0x7f436096, 0x7200464f, 0x76c15bf8,
        0x68860bfd, 0x6c47164a, 0x61043093, 0x65c52d24,
        0x119b4be9, 0x155a565e, 0x18197087, 0x1cd86d30,
        0x029f3d35, 0x065e2082, 0x0b1d065b, 0x0fdc1bec,
        0x3793a651, 0x3352bbe6, 0x3e119d3f, 0x3ad08088,
        0x2497d08d, 0x2056cd3a, 0x2d15ebe3, 0x29d4f654,
        0xc5a92679.toInt(), 0xc1683bce.toInt(), 0xcc2b1d17.toInt(), 0xc8ea00a0.toInt(),
        0xd6ad50a5.toInt(), 0xd26c4d12.toInt(), 0xdf2f6bcb.toInt(), 0xdbee767c.toInt(),
        0xe3a1cbc1.toInt(), 0xe760d676.toInt(), 0xea23f0af.toInt(), 0xeee2ed18.toInt(),
        0xf0a5bd1d.toInt(), 0xf464a0aa.toInt(), 0xf9278673.toInt(), 0xfde69bc4.toInt(),
        0x89b8fd09.toInt(), 0x8d79e0be.toInt(), 0x803ac667.toInt(), 0x84fbdbd0.toInt(),
        0x9abc8bd5.toInt(), 0x9e7d9662.toInt(), 0x933eb0bb.toInt(), 0x97ffad0c.toInt(),
        0xafb010b1.toInt(), 0xab710d06.toInt(), 0xa6322bdf.toInt(), 0xa2f33668.toInt(),
        0xbcb4666d.toInt(), 0xb8757bda.toInt(), 0xb5365d03.toInt(), 0xb1f740b4.toInt()
    )

    fun getCRC32(array: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFFFFFFFF.toInt()
        for (i in offset until length) {
            crc = (crc shl 8 and 0xFFFFFFFF.toInt()) xor crcTable[((crc shr 24) xor array[i].toInt()) and 0xFF]
        }
        return crc
    }
}