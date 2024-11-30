const AwsHelper = require('../../aws-helper');

//call this whenever user stops typing so that it can be saved everytime changes are made
exports.UpdateFileContent = async (req, res) => {
    if (!req.body.bucketName || !req.body.key || !req.body.updatedContent) {
        return res.status(400).json({ msg: "Missing required parameters." });
    }
    
    const bucketExists = await AwsHelper.bucketExists(req.body.bucketName);
    if (!bucketExists) {
      return res.status(404).json({ msg: "Bucket not found." });
    }

    try {
        const fileExists = await AwsHelper.checkFileExists(req.body.bucketName, req.body.key);
        if (!fileExists) {
            await AwsHelper.uploadFile(req.body.bucketName, req.body.key, req.body.updatedContent);
        } else {
            await AwsHelper.updateFile(req.body.bucketName, req.body.key, req.body.updatedContent);
        }

        return res.status(200).body(null);
    } catch (err) {
        return res.status(500).json({ error: error.message });
    }
}