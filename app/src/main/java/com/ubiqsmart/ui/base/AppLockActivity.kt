package com.ubiqsmart.ui.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.ubiqsmart.R
import com.ubiqsmart.interfaces.FingerprintListener
import com.ubiqsmart.utils.AppLockUtils
import com.ubiqsmart.utils.FingerprintHelper
import me.zhanghai.android.patternlock.PatternUtils
import me.zhanghai.android.patternlock.PatternView
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey

class AppLockActivity : BasePatternActivity(), PatternView.OnPatternListener, FingerprintListener {

  private var numFailedAttempts = 0

  private var hasFingerprintSupport: Boolean = false

  private var fingerprintcontainer: LinearLayout? = null

  private var fingerprintHelper: FingerprintHelper? = null
  private var keyStore: KeyStore? = null
  private var cipher: Cipher? = null
  private var fingerprintManager: FingerprintManager? = null
  private var cryptoObject: FingerprintManager.CryptoObject? = null

  private var unlockedWithoutFprint: Boolean = false

  private var sharedPreferences: SharedPreferences? = null

  override fun onBackPressed() {
    unlockedFirst = false
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    messageText.setText(R.string.pl_draw_pattern_to_unlock)
    //patternView.setInStealthMode(true);
    patternView.setOnPatternListener(this)
    fingerprintcontainer = findViewById(R.id.fingerprint_container)
    hasFingerprintSupport = AppLockUtils.hasDeviceFingerprintSupport(this)

    if (hasFingerprintSupport()) {
      sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
      setupFingerprintStuff()
    }

    fingerprintcontainer!!.visibility = if (hasFingerprintSupport) View.VISIBLE else View.GONE

    numFailedAttempts = savedInstanceState?.getInt("num_failed_attempts") ?: 0
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  fun setupFingerprintStuff() {
    fingerprintManager = this.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
    fingerprintHelper = FingerprintHelper(this)
    try {
      generateKey()

      if (cipherInit()) {
        cryptoObject = FingerprintManager.CryptoObject(cipher!!)
        fingerprintHelper!!.startAuth(fingerprintManager, cryptoObject)
      }

    } catch (ex: Exception) {
      ex.printStackTrace()
    }

  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  fun cipherInit(): Boolean {
    try {
      cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7)
    } catch (e: NoSuchAlgorithmException) {
      e.printStackTrace()
    } catch (e: NoSuchPaddingException) {
      e.printStackTrace()
    }

    return try {
      keyStore!!.load(null)
      val key = keyStore!!.getKey("Lunary", null) as SecretKey
      cipher!!.init(Cipher.ENCRYPT_MODE, key)
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }

  }

  @RequiresApi(api = Build.VERSION_CODES.M) protected fun generateKey() {
    try {
      keyStore = KeyStore.getInstance("AndroidKeyStore")
    } catch (e: Exception) {
      e.printStackTrace()
      return
    }

    try {
      val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

      keyStore!!.load(null)
      keyGenerator.init(
          KeyGenParameterSpec.Builder("Lunary", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
              .setUserAuthenticationRequired(true)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
              .build())
      keyGenerator.generateKey()
    } catch (e: Exception) {
      e.printStackTrace()
    }

  }

  override fun onSaveInstanceState(outState: Bundle?) {
    super.onSaveInstanceState(outState)
    outState!!.putInt("num_failed_attempts", numFailedAttempts)
  }

  override fun onPatternDetected(pattern: List<PatternView.Cell>) {
    if (sharedPreferences!!.getLong("WRONG_PATTERN_LOCK", 0) != 0L && sharedPreferences!!.getLong("WRONG_PATTERN_LOCK", 0) > System.currentTimeMillis() - 60 * 1000) {
      messageText.setText(R.string.locked_for_one_minute)
      postClearPatternRunnable()
      return
    }

    if (isPatternCorrect(pattern)) {
      unlockedWithoutFprint = true
      numFailedAttempts = 0
      onConfirmed()
    } else {
      messageText.setText(R.string.pl_wrong_pattern)
      patternView.displayMode = PatternView.DisplayMode.Wrong
      postClearPatternRunnable()
      onWrongPattern()
    }
  }

  fun hasFingerprintSupport(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasFingerprintSupport
  }

  override fun onPause() {
    super.onPause()

    if (fingerprintHelper != null && hasFingerprintSupport()) {
      fingerprintHelper!!.cancel()
    }
  }

  public override fun onResume() {
    super.onResume()

    if (sharedPreferences == null) {
      sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    if (fingerprintHelper != null && hasFingerprintSupport()) {
      setupFingerprintStuff()
    }
  }

  protected fun isPatternCorrect(pattern: List<PatternView.Cell>): Boolean {
    return PatternUtils.patternToSha1String(pattern) == sharedPreferences!!.getString("APP_LOCK_PATTERN", "")
  }

  protected fun onConfirmed() {
    setResult(Activity.RESULT_OK)
    finish()
  }

  protected fun onWrongPattern() {
    ++numFailedAttempts
    if (numFailedAttempts >= 5) {
      val editor = sharedPreferences!!.edit()
      editor.putLong("WRONG_PATTERN_LOCK", System.currentTimeMillis())
      editor.commit()
      messageText.setText(R.string.locked_one_minute)
      numFailedAttempts = 0
    }
  }

  override fun onPatternStart() {
    removeClearPatternRunnable()
    patternView.displayMode = PatternView.DisplayMode.Correct
  }

  override fun onPatternCleared() {
    removeClearPatternRunnable()
  }

  override fun onPatternCellAdded(pattern: List<PatternView.Cell>) {}

  override fun authenticationFailed(error: String) {
    Log.d("fingerprintauth", "FAILED: " + error)
    if (!unlockedWithoutFprint && error != "Fingerprint operation cancelled.") {
      Toast.makeText(this, "You are not authorized!", Toast.LENGTH_SHORT).show()
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.M) override fun authenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
    Log.d("fingerprintauth", "SUCCESS!")
    onConfirmed()
  }

  companion object {

    val REQUEST_CODE = 1000

    private var pausedFirst: Boolean = false
    private var unlockedFirst: Boolean = false

    fun protectWithLock(c: Activity, onResume: Boolean) {
      val preferences = PreferenceManager.getDefaultSharedPreferences(c.applicationContext)

      if (!preferences.getBoolean("use_app_lock", false)) {
        return
      }

      Log.d("secureactivity", onResume.toString() + " ||>> " + unlockedFirst)

      if (!onResume && unlockedFirst) { //pausedFirst
        val editor = preferences.edit()
        editor.putLong("APP_UNLOCKED", System.currentTimeMillis())
        editor.apply()
      }

      // Ask for login if pw protection is enabled and last login is more than 4 minutes ago
      if (preferences.getLong("APP_UNLOCKED", 0) <= System.currentTimeMillis() - 4 * 60 * 1000 && onResume && !pausedFirst && preferences.getString(
          "APP_LOCK_PATTERN", "") != "") {
        val patternLock = Intent(c, AppLockActivity::class.java)
        c.startActivityForResult(patternLock, AppLockActivity.REQUEST_CODE)
      }
      pausedFirst = onResume
    }

    fun handleLockResponse(c: Activity, resultCode: Int) {
      unlockedFirst = if (resultCode != Activity.RESULT_OK) {
        c.finish()
        false
      } else {
        val preferences = PreferenceManager.getDefaultSharedPreferences(c.applicationContext)
        val editor = preferences.edit()
        editor.putLong("APP_UNLOCKED", System.currentTimeMillis())
        editor.apply()
        true
      }
    }
  }
}
