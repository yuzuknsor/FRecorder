package com.vdo.frecorder.data.database

/**
 * 数据库里的记录与磁盘文件对不上时（文件被外部删除等）的回调。
 */
interface OnRecordsLostListener {
    fun onLostRecords(list: MutableList<Record>)
}
