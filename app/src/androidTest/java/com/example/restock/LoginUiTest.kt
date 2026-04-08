package com.example.restock

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginUiTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    @Test
    fun testLoginComponentsAreVisible() {
        // Verifica se os campos principais de login aparecem no ecrã
        onView(withId(R.id.emailInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.passwordInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.loginBtn)).check(matches(isDisplayed()))
        onView(withId(R.id.googleSignInButton)).check(matches(isDisplayed()))
    }
}