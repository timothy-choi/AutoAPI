const express = require('express');
const router = express.Router();
const MfaController = require('./MfaController');

router.post("/verifyMFA", MfaController.verifyMFA);

module.exports = router;