package com.example.restock

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** HUGO MOREIRA - a22402246
 * Testes de Interface (UI/UX Validation) para o ecrã de login.
 * Valida a renderização correta dos componentes e a disponibilidade
 * dos serviços de autenticação do Google.
 */
@RunWith(AndroidJUnit4::class)
class LoginUiTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    // ─── Renderização correta dos componentes ───────────────────────────────

    @Test
    fun testLoginComponentsAreVisible() {
        onView(withId(R.id.emailInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.passwordInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.loginBtn)).check(matches(isDisplayed()))
        onView(withId(R.id.googleSignInButton)).check(matches(isDisplayed()))
    }

    @Test
    fun testSecondaryButtonsAreVisible() {
        onView(withId(R.id.forgotPasswordBtn)).check(matches(isDisplayed()))
        onView(withId(R.id.goRegisterBtn)).check(matches(isDisplayed()))
    }

    @Test
    fun testLogoIsDisplayed() {
        onView(withId(R.id.logo)).check(matches(isDisplayed()))
    }

    @Test
    fun testLoginCardTitleAndSubtitleAreDisplayed() {
        onView(withText(R.string.login_card_title)).check(matches(isDisplayed()))
        onView(withText(R.string.login_card_subtitle)).check(matches(isDisplayed()))
    }

    @Test
    fun testProgressBarInitiallyHidden() {
        onView(withId(R.id.progressBar)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testLoginButtonHasCorrectText() {
        onView(withId(R.id.loginBtn)).check(matches(withText(R.string.login)))
    }

    @Test
    fun testGoogleSignInButtonHasCorrectText() {
        onView(withId(R.id.googleSignInButton)).check(matches(withText(R.string.sign_in_with_google)))
    }

    // ─── Estado inicial dos controlos ───────────────────────────────────────

    @Test
    fun testAllInteractiveElementsInitiallyEnabled() {
        onView(withId(R.id.loginBtn)).check(matches(isEnabled()))
        onView(withId(R.id.googleSignInButton)).check(matches(isEnabled()))
        onView(withId(R.id.forgotPasswordBtn)).check(matches(isEnabled()))
        onView(withId(R.id.goRegisterBtn)).check(matches(isEnabled()))
    }

    @Test
    fun testLoginButtonIsClickable() {
        onView(withId(R.id.loginBtn)).check(matches(isClickable()))
    }

    @Test
    fun testGoogleSignInButtonIsClickable() {
        onView(withId(R.id.googleSignInButton)).check(matches(isClickable()))
    }

    // ─── Disponibilidade dos serviços de autenticação do Google ────────────

    @Test
    fun testGooglePlayServicesAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        assert(result == ConnectionResult.SUCCESS) {
            "Google Play Services não disponíveis no dispositivo (código: $result)"
        }
    }

    // ─── Resiliência: campos vazios não avançam o fluxo ────────────────────

    @Test
    fun testLoginWithEmptyFieldsStaysOnScreen() {
        onView(withId(R.id.loginBtn)).perform(click())
        // Sem credenciais, a activity não deve navegar — os campos mantêm-se visíveis
        onView(withId(R.id.emailInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.passwordInputLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.loginBtn)).check(matches(isDisplayed()))
    }
}
