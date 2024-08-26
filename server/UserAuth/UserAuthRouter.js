const express = require('express');
const router = express.Router();
const UserAuthController = require('./UserAuthController');

router.post('/register', UserAuthController.register);

router.post('/login', UserAuthController.login);

router.post('/logout', UserAuthController.logout);

module.exports = router;