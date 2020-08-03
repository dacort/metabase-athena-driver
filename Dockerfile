FROM metabase/metabase:v0.36.2

# A metabase user/group is manually added in https://github.com/metabase/metabase/blob/master/bin/docker/run_metabase.sh
# Make the UID and GID match
ADD --chown=2000:2000 \
    https://github.com/dacort/metabase-athena-driver/releases/download/v1.1.0/athena.metabase-driver.jar \
    /plugins/athena.metabase-driver.jar
