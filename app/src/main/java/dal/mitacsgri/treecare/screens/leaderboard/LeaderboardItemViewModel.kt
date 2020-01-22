package dal.mitacsgri.treecare.screens.leaderboard

import android.text.SpannedString
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.toObject
import dal.mitacsgri.treecare.consts.CHALLENGE_TYPE_AGGREGATE_BASED
import dal.mitacsgri.treecare.consts.CHALLENGE_TYPE_DAILY_GOAL_BASED
import dal.mitacsgri.treecare.extensions.default
import dal.mitacsgri.treecare.extensions.notifyObserver
import dal.mitacsgri.treecare.extensions.xnor
import dal.mitacsgri.treecare.model.Challenge
import dal.mitacsgri.treecare.model.Challenger
import dal.mitacsgri.treecare.model.User
import dal.mitacsgri.treecare.model.UserChallenge
import dal.mitacsgri.treecare.repository.FirestoreRepository
import dal.mitacsgri.treecare.repository.SharedPreferencesRepository

class LeaderboardItemViewModel(
    private val firestoreRepository: FirestoreRepository,
    private val sharedPrefsRepository: SharedPreferencesRepository
    ) : ViewModel() {

    private lateinit var challenge: Challenge
    private var userChallenge: UserChallenge? = null

    private val mChallengersList = MutableLiveData<ArrayList<Challenger>>()

    var isDialogDisplayed: Boolean
        get() = challenge.active.xnor(userChallenge?.isActive ?: challenge.active)
        set(value) {
            userChallenge?.let {
                it.isActive = value

                val user = sharedPrefsRepository.user
                user.currentChallenges[it.name] = it
                sharedPrefsRepository.user = user

                firestoreRepository.updateUserData(
                    sharedPrefsRepository.user.uid,
                    mapOf("currentChallenges" to user.currentChallenges)
                )
            }
        }

    fun isCurrentUser(challenger: Challenger) = challenger.uid == sharedPrefsRepository.user.uid

    fun getChallengersList(challengeName: String): LiveData<ArrayList<Challenger>> {
        val challengersList = MutableLiveData<ArrayList<Challenger>>().default(arrayListOf())

        firestoreRepository.getChallenge(challengeName)
            .addOnSuccessListener {
                challenge = it.toObject<Challenge>()!!

                userChallenge = sharedPrefsRepository.user.currentChallenges[challengeName]

                val challengers = challenge.players
                val challengersCount = challengers.size
                val limit = if (challengersCount > 10) 10 else challengersCount

                for (i in 0 until limit) {
                    firestoreRepository.getUserData(challengers[i])
                        .addOnSuccessListener {
                            val user = it.toObject<User>()
                            val challenger = user?.let { makeChallengerFromUser(user, challenge) }
                            challengersList.value?.add(challenger!!)

                            if (challengersList.value?.size == limit) {
                                challengersList.value?.sortChallengersList(challenge.type)
                                challengersList.notifyObserver()
                            }
                        }
                }
            }

        mChallengersList.value = challengersList.value
        return challengersList
    }

    fun getCurrentChallengerPositionText(): String = getCurrentChallengerPosition().toString()

    fun getCurrentChallengerPosition(): Int {
        val currentUserUid = sharedPrefsRepository.user.uid
        val challengers = mChallengersList.value ?: arrayListOf()
        for (i in 0 until challengers.size) {
            if (challengers[i].uid == currentUserUid)
                return i+1
        }
        return 0
    }

    fun getChallengeName(): String = sharedPrefsRepository.challengeName

    fun getTotalStepsText(challenger: Challenger): SpannedString =
        buildSpannedString {
            bold {
                append("Total steps: ")
            }
            append(challenger.totalSteps.toString())
        }

    fun getLeafCountText(challenger: Challenger): String = challenger.totalLeaves.toString()

    private fun makeChallengerFromUser(user: User, challenge: Challenge): Challenger {
        val userChallengeData = user.currentChallenges[challenge.name]!!

        return Challenger(
            name = user.name,
            uid = user.uid,
            photoUrl = user.photoUrl,
            challengeGoalStreak = userChallengeData.challengeGoalStreak,
            totalSteps = userChallengeData.totalSteps,
            totalLeaves = userChallengeData.leafCount)
    }

    private fun ArrayList<Challenger>.sortChallengersList(challengeType: Int) {
        sortByDescending {
            when(challengeType) {
                CHALLENGE_TYPE_DAILY_GOAL_BASED -> it.totalSteps
                CHALLENGE_TYPE_AGGREGATE_BASED -> it.totalSteps
                else -> it.totalSteps
            }
        }
    }
}