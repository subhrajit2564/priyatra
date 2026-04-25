package com.priyatra.guide.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.priyatra.guide.data.StoredTrip

@Dao
abstract class TripDao {
    @Query("SELECT COUNT(*) FROM trips")
    abstract fun count(): Int

    @Transaction
    @Query("SELECT * FROM trips")
    abstract fun getAllWithPhones(): List<TripWithPhonesRow>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract fun getByIdWithPhones(id: String): TripWithPhonesRow?

    @Query("DELETE FROM customer_phones WHERE trip_id = :tid")
    protected abstract fun deletePhonesForTripId(tid: String)

    @Query("DELETE FROM trips WHERE id NOT IN (:ids)")
    protected abstract fun deleteTripsNotInInternal(ids: List<String>)

    @Query("DELETE FROM trips")
    abstract fun deleteAllTrips()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertTripEntity(e: TripEntity)

    @Insert
    protected abstract fun insertPhoneRows(phones: List<CustomerPhoneEntity>)

    @Transaction
    open fun replaceCatalog(trips: List<StoredTrip>) {
        if (trips.isEmpty()) {
            deleteAllTrips()
            return
        }
        val keep = trips.map { it.id }
        deleteTripsNotInInternal(keep)
        for (t in trips) {
            val entity = TripEntityMappers.toEntity(t)
            insertTripEntity(entity)
            deletePhonesForTripId(t.id)
            val rows = TripEntityMappers.toPhoneRows(t)
            if (rows.isNotEmpty()) {
                insertPhoneRows(rows)
            }
        }
    }
}
