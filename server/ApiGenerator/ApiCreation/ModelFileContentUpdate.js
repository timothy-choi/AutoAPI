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

    const fileExists = await AwsHelper.checkFileExists(req.body.bucketName, req.body.key);
    if (!fileExists) {
      return res.status(404).json({ msg: "File not found in the specified bucket." });
    }

    try {
        await AwsHelper.updateFile(req.body.bucketName, req.body.key, req.body.updatedContent).promise();

        return res.status(200).body(null);
    } catch (err) {
        return res.status(500).json({ error: error.message });
    }
}