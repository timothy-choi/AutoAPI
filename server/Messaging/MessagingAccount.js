const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const MessagingAccount = sequelize.define('MessagingAccount', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    UserId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    Username: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    AllChatroomsJoined: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    },
    ChatroomsToJoin: { //chatrooms that the user is invited to join
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: []
    }
});

module.exports = MessagingAccount;