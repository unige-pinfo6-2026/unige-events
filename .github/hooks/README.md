# Git Hooks

Pour activer les hooks localement (une seule fois par machine) :

    git config core.hooksPath .github/hooks

Ces hooks vérifient :
- Qu'aucun champ booléen n'est préfixé `is` dans les entités (conflit Lombok + JSON incohérent)
- Que la documentation est mise à jour quand du code critique change

Pour bypasser (à utiliser avec parcimonie) :

    git commit --no-verify
