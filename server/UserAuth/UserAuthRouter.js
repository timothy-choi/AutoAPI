const express = require('express');
const router = express.Router();
const UserAuthController = require('./UserAuthController');

router.post('/register', UserAuthController.register);

router.post('/login', UserAuthController.login);

router.post('/logout', UserAuthController.logout);

router.delete('/deleteUserAuth', UserController.deleteUser);

router.put('/editUsername', UserAuthController.replaceUsername);

router.put('/editPassword', UserAuthController.replacePassword);

module.exports = router;