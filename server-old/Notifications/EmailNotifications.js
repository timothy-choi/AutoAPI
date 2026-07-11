const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const EmailNotification = sequelize.define('EmailNotification', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    UserRecipientId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    EmailRecipient: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    EmailSent: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: Date.now,
    },
    EmailSubject: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    EmailMessage: {
        type: DataTypes.STRING,
        allowNull: false,
    }
});



module.exports = EmailNotification;