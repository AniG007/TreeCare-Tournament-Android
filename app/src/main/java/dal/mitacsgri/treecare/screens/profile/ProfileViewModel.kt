package dal.mitacsgri.treecare.screens.profile

import android.content.Context
import android.util.Log
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.crashlytics.android.Crashlytics
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.firebase.firestore.ktx.toObject
import dal.mitacsgri.treecare.extensions.default
import dal.mitacsgri.treecare.model.UserChallengeTrophies
import dal.mitacsgri.treecare.repository.FirestoreRepository
import dal.mitacsgri.treecare.repository.SharedPreferencesRepository

class ProfileViewModel(
    private val firestoreRepository: FirestoreRepository,
    private val sharedPrefsRepository: SharedPreferencesRepository
) : ViewModel() {

    val trophiesCountData = MutableLiveData<Triple<Int, Int, Int>>().default(Triple(0, 0, 0))

    fun getUserPhotoUrl() = sharedPrefsRepository.user.photoUrl

    fun getUserFullName() = sharedPrefsRepository.user.name

    fun getUserFirstName() = sharedPrefsRepository.user.name.split(' ')[0]

    fun getDailyGoalCompletionStreakCount() = sharedPrefsRepository.dailyGoalStreak

    fun getWeeklyDailyGoalAchievedCount() = sharedPrefsRepository.dailyGoalAchievedCount

    fun getCurrentWeekDayForStreak() = sharedPrefsRepository.currentDayOfWeek

    fun getGoalCompletionStreakData(): Array<Boolean> {
        val goalCompletionString = sharedPrefsRepository.dailyGoalStreakString
        val boolArray = arrayOf(false, false, false, false, false, false, false)

        for (i in 0 until goalCompletionString.length) {
            boolArray[i] = goalCompletionString[i] == '1'
        }

        return boolArray
    }

    fun getDailyGoalStreakText() = buildSpannedString {
        val count = getWeeklyDailyGoalAchievedCount()
        append("You achieved ")
        bold {
            append("$count daily " +
                    if (getWeeklyDailyGoalAchievedCount() > 1) "goals" else "goal")
        }
        append(" in ")
        bold {
            append("the last ${sharedPrefsRepository.currentDayOfWeek + 1} " +
                    if ((sharedPrefsRepository.currentDayOfWeek + 1) > 1) "days" else "day")
        }
    }

    fun getTrophiesCount() {
        firestoreRepository.getTrophiesData(sharedPrefsRepository.user.uid)
            .addOnSuccessListener {
                val userTrophies = it.toObject<UserChallengeTrophies>()
                userTrophies?.let {
                    trophiesCountData.value = Triple(
                        userTrophies.gold.size,
                        userTrophies.silver.size,
                        userTrophies.bronze.size)
                }
            }
            .addOnFailureListener {
                Log.d("Profile", "Failed to obtain trophies data")
            }
    }

    fun updateUserName(newName: String, successAction: () -> Unit) {
        firestoreRepository.updateUserData(sharedPrefsRepository.user.uid, mapOf("name" to newName))
            .addOnSuccessListener {
                updateUserNameInSharedPrefs(newName)
                successAction()
                Log.d("UserName", "Name changed to $newName")
            }
            .addOnFailureListener {
                Log.d("UserName", "Name change failed")
            }
    }

    fun logout(context: Context) {
        sharedPrefsRepository.clearSharedPrefs()
        Fitness.getConfigClient(context, GoogleSignIn.getLastSignedInAccount(context)!!).disableFit()
        Crashlytics.setUserIdentifier("")
        Crashlytics.setUserName("")
        Crashlytics.setUserEmail("")
        //TODO: Maybe cancellation of jobs is needed
    }

    private fun updateUserNameInSharedPrefs(newName: String) {
        val user = sharedPrefsRepository.user
        user.name = newName
        sharedPrefsRepository.user = user
    }
}
