const express = require('express');
const router = express.Router();
const UserStatsController = require('./UserStatsController');

router.get('/:userStatsId', UserStatsController.GetUserStatsById);

router.get('/userId/:userId', UserStatsController.GetUserStatsByUserId);

router.post('/', UserStatsController.CreateUserStats);

router.delete('/:userStatsId', UserStatsController.DeleteUserStats);

module.exports = router;