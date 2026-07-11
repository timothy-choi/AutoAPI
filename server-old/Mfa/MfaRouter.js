const express = require('express');
const router = express.Router();
const MfaController = require('./MfaController');

router.post("/verifyMFA", MfaController.verifyMFA);

router.post("/enableMFA", MfaController.enableMFA);

router.post("/disableMFA", MfaController.disableMFA);

module.exports = router;