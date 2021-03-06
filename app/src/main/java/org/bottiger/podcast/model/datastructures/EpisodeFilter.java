package org.bottiger.podcast.model.datastructures;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.bottiger.podcast.provider.IEpisode;

/**
 * Created by aplb on 20-01-2016.
 */
public class EpisodeFilter {

    private String mSearchQuery;

    public boolean match(@Nullable IEpisode argEpisode) {
        if (argEpisode == null)
            return false;

        if (TextUtils.isEmpty(mSearchQuery))
            return true;

        String title = argEpisode.getTitle();
        if (matchString(title))
            return true;

        String description = argEpisode.getDescription();
        if (matchString(description))
            return true;

        return false;
    }

    public void setSearchQuery(@Nullable String argQuery) {
        mSearchQuery = argQuery != null ? argQuery.toLowerCase() : null;
    }

    @Nullable
    public String getSearchQuery() {
        return mSearchQuery;
    }

    private boolean matchString(@Nullable String argString) {
        if (argString == null)
            return false;

        return argString.toLowerCase().contains(mSearchQuery);
    }

}
