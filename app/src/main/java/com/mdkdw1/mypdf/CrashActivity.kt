package com.mdkdw1.mypdf

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stack = intent.getStringExtra("stack") ?: "(스택 없음)"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        root.addView(TextView(this).apply {
            text = "⚠ 앱이 중단되었습니다"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        scroll.addView(TextView(this).apply {
            text = stack
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(scroll)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnRow.addView(Button(this).apply {
            text = "로그 보기"
            setOnClickListener {
                startActivity(Intent(this@CrashActivity, LogActivity::class.java))
            }
        })

        btnRow.addView(Button(this).apply {
            text = "공유"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "MyPDF 크래시")
                    putExtra(Intent.EXTRA_TEXT, stack)
                }
                startActivity(Intent.createChooser(intent, "공유"))
            }
        })

        btnRow.addView(Button(this).apply {
            text = "앱 재시작"
            setOnClickListener {
                val i = packageManager.getLaunchIntentForPackage(packageName)
                i?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                finish()
            }
        })

        root.addView(btnRow)
        setContentView(root)
    }
}
