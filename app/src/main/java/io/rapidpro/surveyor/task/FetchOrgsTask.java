package io.rapidpro.surveyor.task;

import java.util.HashSet;
import java.util.Set;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.data.OrgService;
import io.rapidpro.surveyor.net.responses.Token;
import io.rapidpro.surveyor.utils.AppExecutors;

/**
 * Fetches orgs from RapidPro, creates their directories, saves their details, and returns their UUIDs.
 * Runs on a background executor (replaces the deprecated AsyncTask).
 */
public class FetchOrgsTask {

    private final Listener listener;
    private boolean failed;

    public FetchOrgsTask(Listener listener) {
        this.listener = listener;
    }

    public void execute(final Token... tokens) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                final Set<String> orgUUIDs = fetch(tokens);

                AppExecutors.runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        if (failed) {
                            listener.onFailure();
                        } else {
                            listener.onComplete(orgUUIDs);
                        }
                    }
                });
            }
        });
    }

    private Set<String> fetch(Token... tokens) {
        OrgService svc = SurveyorApplication.get().getOrgService();

        Set<Org> orgs = new HashSet<>();
        Set<String> orgUUIDs = new HashSet<>();

        for (Token token : tokens) {
            try {
                Org org = svc.getOrFetch(token.getOrg().getUuid(), token.getOrg().getName(), token.getToken());

                orgs.add(org);
                orgUUIDs.add(org.getUuid());

                Logger.d("Fetched org with UUID " + org.getUuid());
            } catch (Exception e) {
                Logger.e("Unable to fetch org", e);
                this.failed = true;
                break;
            }
        }

        return orgUUIDs;
    }

    public interface Listener {
        void onComplete(Set<String> orgUUIDs);

        void onFailure();
    }
}
