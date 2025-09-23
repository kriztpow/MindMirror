package com.example.mindmirror

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import android.widget.Toast

class HistoryActivity : AppCompatActivity() {
    private lateinit var chart: LineChart
    private lateinit var db: DetectionDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        chart = findViewById(R.id.lineChart)
        db = DetectionDbHelper(this)
        loadData()
    }

    private fun loadData() {
        val rows = db.queryAll().reversed() // oldest first
        if (rows.isEmpty()) {
            Toast.makeText(this, "No data yet", Toast.LENGTH_SHORT).show()
            return
        }
        val entries = rows.mapIndexed { idx, rec -> Entry(idx.toFloat(), rec.score) }
        val set = LineDataSet(entries, "Smile probability")
        val data = LineData(set)
        chart.data = data
        chart.invalidate()
    }
}
