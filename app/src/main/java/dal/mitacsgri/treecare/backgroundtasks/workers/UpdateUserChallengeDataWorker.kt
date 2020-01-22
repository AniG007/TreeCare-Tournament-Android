package dal.mitacsgri.treecare.backgroundtasks.workers

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import calculateLeafCountFromStepCount
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.firebase.Timestamp
import dal.mitacsgri.treecare.consts.CHALLENGE_TYPE_DAILY_GOAL_BASED
import dal.mitacsgri.treecare.extensions.toDateTime
import dal.mitacsgri.treecare.model.User
import dal.mitacsgri.treecare.model.UserChallenge
import dal.mitacsgri.treecare.repository.FirestoreRepository
import dal.mitacsgri.treecare.repository.SharedPreferencesRepository
import dal.mitacsgri.treecare.repository.StepCountRepository
import org.joda.time.DateTime
import org.joda.time.Days
import org.koin.core.KoinComponent
import org.koin.core.inject

class UpdateUserChallengeDataWorker(appContext: Context, workerParams: WorkerParameters)
    : ListenableWorker(appContext, workerParams), KoinComponent {

    private val stepCountRepository: StepCountRepository by inject()
    private val sharedPrefsRepository: SharedPreferencesRepository by inject()
    private val firestoreRepository: FirestoreRepository by inject()

    override fun startWork(): ListenableFuture<Result> {
        val future = SettableFuture.create<Result>()
        val user = sharedPrefsRepository.user

        user.currentChallenges.forEach { (_, challenge) ->

            val endTimeMillis = challenge.endDate.toDateTime().millis
            //Two condition checks are applied because the 'isActive' variable is set only after
            //the dialog has been displayed. The second condition check prevents update of challenge step count
            //in the database even when the dialog has not been displayed
            if (challenge.isActive && endTimeMillis > DateTime().millis) {
                if (challenge.type == CHALLENGE_TYPE_DAILY_GOAL_BASED) {
                    stepCountRepository.getTodayStepCountData {
                        challenge.dailyStepsMap[DateTime().withTimeAtStartOfDay().millis.toString()] = it
                        updateAndStoreUserChallengeDataInSharedPrefs(challenge, user)
                    }
                }
            }
        }
        updateUserChallengeDataInFirestore(future)

        return future
    }

    private fun getChallengeGoalStreakForUser(challenge: UserChallenge, user: User): Int {
        val userChallengeData = user.currentChallenges[challenge.name]!!
        var streakCount = 0

        userChallengeData.dailyStepsMap.forEach { (date, stepCount) ->
            //This check prevents resetting streak count if goal is yet to be met today
            if (date.toLong() < DateTime().withTimeAtStartOfDay().millis) {
                if (stepCount >= challenge.goal) streakCount++
                else streakCount = 0
            }
        }
        return streakCount
    }

    private fun updateAndStoreUserChallengeDataInSharedPrefs(challenge: UserChallenge, user: User) {
        challenge.leafCount = getTotalLeafCountForChallenge(challenge)
        challenge.fruitCount = getTotalFruitCountForChallenge(challenge)
        challenge.challengeGoalStreak = getChallengeGoalStreakForUser(challenge, user)
        challenge.lastUpdateTime = Timestamp.now()

        var totalSteps = 0
        challenge.dailyStepsMap.forEach { (time, steps) ->
            totalSteps += steps
        }
        challenge.totalSteps = totalSteps

        synchronized(sharedPrefsRepository.user) {
            val user = sharedPrefsRepository.user
            user.currentChallenges[challenge.name] = challenge
            sharedPrefsRepository.user = user
        }
    }

    private fun updateUserChallengeDataInFirestore(future: SettableFuture<Result>) {

        firestoreRepository.updateUserData(sharedPrefsRepository.user.uid,
            mapOf("currentChallenges" to sharedPrefsRepository.user.currentChallenges))
            .addOnSuccessListener {
                Log.d("Worker", "User data upload success")
                future.set(Result.success())
            }
            .addOnFailureListener {
                Log.e("Worker", "User data upload failed")
                future.set(Result.failure())
            }
    }

    private fun getTotalLeafCountForChallenge(challenge: UserChallenge): Int {
        val stepsMap = challenge.dailyStepsMap
        val goal = challenge.goal
        var leafCount = 0

        val keys = stepsMap.keys.sortedBy {
            it.toLong()
        }

        for (i in 0 until keys.size-1) {
            leafCount += calculateLeafCountFromStepCount(stepsMap[keys[i]]!!, goal)
        }
        leafCount += stepsMap[keys[keys.size-1]]!! / 1000

        return leafCount
    }

    private fun getTotalFruitCountForChallenge(challenge: UserChallenge): Int {
        val joinDate = DateTime(challenge.joinDate)
        val currentDate = DateTime()
        val days = Days.daysBetween(joinDate, currentDate).days
        val weeks = Math.ceil(days/7.0).toInt()

        var fruitCount = 0

        var weekStartDate = joinDate
        var newWeekDate = weekStartDate.plusWeeks(1)
        var mapPartition: Map<String, Int>
        for (i in 0 until weeks) {
            mapPartition = challenge.dailyStepsMap.filter {
                val keyAsLong = it.key.toLong()
                keyAsLong >= weekStartDate.millis && keyAsLong < newWeekDate.millis
            }
            fruitCount += calculateFruitCountForWeek(challenge, mapPartition)
            weekStartDate = newWeekDate
            newWeekDate = weekStartDate.plusWeeks(1)
        }

        return fruitCount
    }

    /**
     * @param challenge UserChallenge
     * @param stepCountMap Step count map for a week
     * @return +1, 0 or -1 as fruit count
     */
    private fun calculateFruitCountForWeek(challenge: UserChallenge, stepCountMap: Map<String, Int>): Int {
        var currentDay = 0
        val goalAchievedStreak = arrayOf(false, false, false, false, false, false, false)
        val fullStreak = arrayOf(true, true, true, true, true, true, true)

        if (stepCountMap.size < 7) return 0

        stepCountMap.forEach { (_, stepCount) ->
            goalAchievedStreak[currentDay] =
                stepCount >= challenge.goal
            currentDay++
        }

        return if (goalAchievedStreak.contentEquals(fullStreak)) 1 else -1
    }
}
