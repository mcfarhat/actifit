package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import android.content.SharedPreferences;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;

import java.util.ArrayList;


public class LeaderboardEntryAdapter extends ArrayAdapter<SinglePostModel> {

    private String currentUser = "";
    public LeaderboardEntryAdapter(Context context, ArrayList<SinglePostModel> activityEntry) {
        super(context, 0, activityEntry);

        SharedPreferences sharedPreferences = context.getSharedPreferences("actifitSets", context.MODE_PRIVATE);
        currentUser = sharedPreferences.getString("actifitUser","");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        final SinglePostModel postEntry = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.activity_leaderboard_single_entry, parent, false);
        }

        // Lookup view for data population
        LinearLayout entryContainer = convertView.findViewById(R.id.entryContainer);
        TextView entryRank = convertView.findViewById(R.id.entryRank);
        TextView userName = convertView.findViewById(R.id.userName);
        TextView entryCount = convertView.findViewById(R.id.activityCount);
        ImageView userProfilePic = convertView.findViewById(R.id.userProfilePic);
        Button detailsButton = convertView.findViewById(R.id.entryDetailsBtn);

        // Populate the data into the template view using the data object

        entryRank.setText("" + postEntry.leaderRank);
        userName.setText(postEntry.username);

        final Context leaderboardContext = this.getContext();

        //set proper image

        Picasso.with(leaderboardContext).load(postEntry.userProfilePic).into(userProfilePic);

        //highlight the user's entry in the list
        if (!currentUser.equals("") && currentUser.equals(postEntry.username) ){
            entryContainer.setBackgroundColor(getContext().getResources().getColor(R.color.actifitRed));
        }else{
            entryContainer.setBackgroundColor(getContext().getResources().getColor(R.color.colorWhite));
        }

        //decimal format the numbers to add thousands separator
        DecimalFormat decim = new DecimalFormat("#,###");

        entryCount.setText(decim.format(postEntry.activityCount));

        //render some visual effects for step count
        if (postEntry.activityCount >= 10000 ){
            entryCount.setTextColor(getContext().getResources().getColor(R.color.actifitGreen));
        }else if (postEntry.activityCount >= 5000 ){
            entryCount.setTextColor(getContext().getResources().getColor(R.color.actifitRed));
        }else {
            entryCount.setTextColor(getContext().getResources().getColor(android.R.color.tab_indicator_text));
        }

        //associate proper action with button
        detailsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                builder.setToolbarColor(leaderboardContext.getResources().getColor(R.color.actifitRed));

                //animation for showing and closing fitbit authorization screen
                builder.setStartAnimations(leaderboardContext, R.anim.slide_in_right, R.anim.slide_out_left);

                //animation for back button clicks
                builder.setExitAnimations(leaderboardContext, android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);

                CustomTabsIntent customTabsIntent = builder.build();

                customTabsIntent.launchUrl(leaderboardContext, Uri.parse(MainActivity.ACTIFIT_CORE_URL + postEntry.postUrl));


            }
        });
        // Return the completed view to render on screen
        return convertView;
    }
}
