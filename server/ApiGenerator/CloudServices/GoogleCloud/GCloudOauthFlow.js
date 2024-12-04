const {OAuth2Client} = require('google-auth-library');

require('dotenv').config();

const GCLOUD_CLIENT_ID = process.env.GCLOUD_CLIENT_ID;
const GCLOUD_CLIENT_SECRET = process.env.GCLOUD_CLIENT_SECRET;
const GCLOUD_REDIRECT_URL = process.env.GCLOUD_REDIRECT_URL;
const GCLOUD_SCOPES = process.env.GCLOUD_SCOPES;

const oauth2Client = OAuth2Client(
    GCLOUD_CLIENT_ID,
    GCLOUD_CLIENT_SECRET,
    GCLOUD_REDIRECT_URL
);

exports.LoginToGCloud = async (req, res) => {
    const url = oauth2Client.generateAuthUrl({
        access_type: 'offline', 
        scope: GCLOUD_SCOPES,
    });
    
    res.redirect(url);
}


