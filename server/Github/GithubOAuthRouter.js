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

module.exports = router;
