package dal.mitacsgri.treecare.screens.challenges.activechallenges

import android.content.DialogInterface
import android.content.Intent
import android.view.View
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dal.mitacsgri.treecare.extensions.disable
import dal.mitacsgri.treecare.extensions.enable
import dal.mitacsgri.treecare.model.Challenge
import dal.mitacsgri.treecare.screens.BaseViewHolder
import dal.mitacsgri.treecare.screens.challenges.ChallengesFragmentDirections
import dal.mitacsgri.treecare.screens.challenges.ChallengesViewModel
import dal.mitacsgri.treecare.screens.treecareunityactivity.TreeCareUnityActivity
import kotlinx.android.synthetic.main.item_active_challenge.view.*

/**
 * Created by Devansh on 25-06-2019
 */

class ActiveChallengesViewHolder(
    itemView: View,
    private val viewModel: ChallengesViewModel
    ): BaseViewHolder<Challenge>(itemView) {

    override fun bind(item: Challenge) {
        itemView.apply {
            nameTV.text = item.name
            descriptionTV.text = item.description
            durationTV.text = viewModel.getChallengeDurationText(item)
            membersCountTV.text = viewModel.getPlayersCountText(item)
            goalTV.text = viewModel.getGoalText(item)

            if (item.active && !viewModel.hasUserJoinedChallenge(item)) {
                buttonJoin.enable()
                buttonJoin.setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(viewModel.getJoinChallengeDialogTitleText(item))
                        .setMessage(viewModel.getJoinChallengeMessageText())
                        .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                            viewModel.joinChallenge(item) {
                                viewModel.startUnityActivityForChallenge(item) {
                                    context.startActivity(Intent(context, TreeCareUnityActivity::class.java))
                                }
                            }
                        }
                        .setNegativeButton("No") { dialogInterface: DialogInterface, _: Int ->
                            dialogInterface.dismiss()
                        }
                        .show()
                }
            } else {
                buttonJoin.disable()
            }

            buttonLeaderBoard.setOnClickListener {
                val action = ChallengesFragmentDirections
                    .actionChallengesFragmentToLeaderboardFragment(item.name)
                findNavController().navigate(action)
            }
        }
    }
}