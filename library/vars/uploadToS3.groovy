def call(Map config = [:]) {

    def profile = config.profile ?: 'default'
    def source = config.source ?: null
    def bucket = config.bucket ?: null
    def key = config.key ?: null

    if (!source || !bucket || !key) {
        error("Missing required parameters: source, bucket, key")
    }
    
    makeBucket(bucket, profile)

    echo("Uploading ${source} to s3://${bucket}/${key}")
    sh("aws s3 cp ${source} s3://${bucket}/${key} --profile ${profile}")
}

def makeBucket(String bucket, String profile) {
    def buckets = sh(script: "aws s3api list-buckets --profile ${profile} --no-cli-pager", returnStdout: true)
    def jsonBuckets = readJSON(text: buckets)
    if (jsonBuckets.Buckets) {
        for (b in jsonBuckets.Buckets) {
            if (b.Name == bucket) {
                return
            }
        }
    }
    echo("Creating S3 bucket ${bucket}")
    sh("aws s3 mb s3://${bucket} --profile ${profile} --no-cli-pager")
}