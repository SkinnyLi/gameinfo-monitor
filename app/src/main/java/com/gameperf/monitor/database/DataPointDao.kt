package com.gameperf.monitor.database

import androidx.room.*

@Dao
interface DataPointDao {

    @Query("SELECT * FROM data_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getDataPointsForSession(sessionId: Long): List<DataPoint>

    @Insert
    suspend fun insertDataPoint(dataPoint: DataPoint)

    @Insert
    suspend fun insertDataPoints(dataPoints: List<DataPoint>)

    @Query("DELETE FROM data_points WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)

    @Query("SELECT COUNT(*) FROM data_points WHERE sessionId = :sessionId")
    suspend fun getDataPointCount(sessionId: Long): Int
}
