const express = require('express');
const passport = require('passport');
const githubCallbackHelper = require('./GithubCallbackHelper');
const passportUtils = require('../passportUtils');

const router = express.Router();

router.get('/github', passportUtils.authenticateWith('github', { scope: ['user:email'] }), githubCallbackHelper.githubAuth);

router.get('/github/callback', 
  authenticateWith('github', { failureRedirect: '/' }), 
  githubCallbackHelper.githubCallback
);

router.get('/github/logout', (req, res, next) => {
    const revokeEndpoint = 'https://github.com/settings/connections/applications/:client_id'; 
    const { accessToken } = req.user;
  
    axios.post(revokeEndpoint, {}, {
      headers: { Authorization: `Bearer ${accessToken}` },
    }).catch(err => console.error('Error revoking token:', err.message));
  
    req.logout(err => {
      if (err) return next(err);
      req.session.destroy(() => {
        res.clearCookie('connect.sid');
      });
    });
});

module.exports = router;
