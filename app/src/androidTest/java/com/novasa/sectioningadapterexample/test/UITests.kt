package com.novasa.sectioningadapterexample.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.novasa.sectioningadapterexample.app.ExampleActivityKotlin
import com.novasa.sectioningadapterexample.app.TestApplication
import com.novasa.sectioningadapterexample.data.DataSource
import com.novasa.sectioningadapterexample.data.Item
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@LargeTest
class UITests {

    @Rule
    val activityRule: ActivityTestRule<ExampleActivityKotlin> = object : ActivityTestRule<ExampleActivityKotlin>(ExampleActivityKotlin::class.java) {

        override fun beforeActivityLaunched() {
            val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestApplication

            application.component.inject(this@UITests)
        }
    }

    private val publisher = PublishSubject.create<List<Item>>()

    @Inject
    lateinit var dataSource: DataSource

    @Before
    fun setup() {
        `when`(dataSource.data()).thenReturn(publisher)
    }

    @Test
    fun testInsert() {
        val items = listOf(
            Item(1, 1),
            Item(2, 2)
        )

        publisher.onNext(items)
    }
}