const express = require('express.js');
const dotenv = require('dotenv');
const session = require('express-session');
const webpush = require('web-push');
const sequelize = require('./config/postgres');
const app = express();
const userAuthRoutes = require('./UserAuth/UserAuthRouter');
const mfaRouter = require('./Mfa/MfaRouter');
const notificationAccountRouter = require('./Notifications/NotificationAccountRouter');
const searchRouter = require('./Search/SearchRouter');
const userRouter = require('./User/UserRouter');
const groupRouter = require('./Group/GroupRouter');
const userStatsRouter = require('./User/UserStatsRouter');
const notificationRouter = require('./Notifications/NotificationRouter');
const emailNotificationRouter = require('./Notifications/EmailNotificationsRouter');
const notificationWorkflowRouter = require('./Notifications/NotificationWorkflowController');
const messageRouter = require('./Messaging/MessageRouter');
const messagingRouter = require('./Messaging/MessagingController');
const messagingSessionRouter = require('./Messaging/MessagingSessionRouter');
const messagingAccountRouter = require('./Messaging/MessagingAccountRouter');
const githubOAuthRouter = require('./Github/GithubOAuthRouter');

const passport = require('passport');
const GitHubStrategy = require('passport-github2').Strategy;

dotenv.config();
require('./config/mongodb');

app.use(express.json());
app.use(express.urlencoded({ extended: false }));

webpush.setVapidDetails(
    process.env.NOTIFICATION_EMAIL,
    process.env.PUBLIC_VAPID_KEY,
    process.env.PRIVATE_VAPID_KEY
);

app.use(session({
    secret: process.env.SESSION_SECRET,
    resave: false,
    saveUninitalized: true,
    cookie: {
        secure: true,
        maxAge: 24 * 60 * 60 * 1000
    }
}));

app.use(passport.initialize());
app.use(passport.session());

passport.use(new GitHubStrategy({
    clientID: process.env.GITHUB_CLIENT_ID,
    clientSecret: process.env.GITHUB_CLIENT_SECRET,
    callbackURL: process.env.GITHUB_CALLBACK_URL,
}, (accessToken, refreshToken, profile, done) => {
    return done(null, { provider: 'github', profile, accessToken, refreshToken });
}));

app.use('/userAuth', userAuthRoutes);

app.use('/mfa', mfaRouter);

app.use('/notificationAccount', notificationAccountRouter);

app.use('/search', searchRouter);

app.use('/user', userRouter);

app.use('/group', groupRouter);

app.use('/userStats', userStatsRouter);

app.use('/notification', notificationRouter);

app.use('/emailNotification', emailNotificationRouter);

app.use('/notificationWorkflow', notificationWorkflowRouter);

app.use('/message', messageRouter);

app.use('/messaging', messagingRouter);

app.use('/messagingSession', messagingSessionRouter);

app.use('/messagingAccount', messagingAccountRouter);

app.use('/github/OAuth', githubOAuthRouter);

passport.serializeUser((user, done) => done(null, user));
passport.deserializeUser((user, done) => done(null, user));

sequelize.sync()
    .then(() => {
        const PORT = process.env.PORT || 3000;
        app.listen(PORT, () => {});
    }).catch((err) => {});