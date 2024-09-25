const express = require('express');
const router = express.Router();
const UserController = require('./UserController');

router.get("/:userId", UserController.GetUserById);

router.get("/username/:username", UserController.GetUserByUsername);

router.post("/", UserController.CreateUser);

router.delete("/:userId", UserController.DeleteUser);

module.exports = router;