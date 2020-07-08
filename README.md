# `@atomist/pr-commit-check-skill`

<!---atomist-skill-readme:start--->

# What it's useful for

Monitor the HEAD commit messages on any incoming pull requests. If they don't conform to a set of rules, then
create a failing github check to block merge of the PR. The current rules are [here](https://github.com/atomist-skills/pr-commit-check-skill/blob/master/src/atomist/main.cljs#L50).

# Before you get started

1.  **GitHub**

The **GitHub** integration must be configured in order to use this skill.
At least one repository must be selected.

# How to configure

1.  **Select repositories**

    By default, this skill will be enabled for all repositories in all organizations you have connected. To restrict
    the organizations or specific repositories on which the skill will run, you can explicitly
    choose organization(s) and repositories.

    Either select all, if all your repositories should participate, or choose a subset of repositories that should
    stay have pull requests monitored.

    ![repo-filter](docs/images/repo-filter.png)

# How to Use

1. **Configure the skill as described above**

1. **Commit and push your code changes**

1. **Enjoy automatic formatting!**

## Integrations

List the Integrations

<!---atomist-skill-readme:end--->

## Developing

1.  Start a shadow-cljs watch process

    ```
    npm run build:watch
    ```

2.  Start up a Node environment

    ```
    node index.js
    ```

3.  Connect an nrepl session at `.nrepl-port` and switch to the Node environment using

    ```
    => (shadow/repl :dev)
    ```

### Testing

```
npm run test
```

## Releasing

We will register new versions of this skill whenever a Git annotated tag is pushed.

```
clj -Arelease patch
```

This will create a new version that can installed into a workspace using the url
[https://go.atomist.com/catalog/skills/atomist/pr-commit-check-skill?stability=unstable](unstable).

Join the [pr-commit-check-skill Slack channel](https://atomist-community.slack.com/archives/C01616DNDN3) to see progress on releasing the Skill.

[unstable]: https://go.atomist.com/catalog/skills/atomist/pr-commit-check-skill?stability=unstable

---

Created by [Atomist][atomist].
Need Help? [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ "Atomist - How Teams Deliver Software"
[slack]: https://join.atomist.com/ "Atomist Community Slack"
